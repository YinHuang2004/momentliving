package com.momentliving.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.momentliving.dto.MerchantApplyDTO;
import com.momentliving.entity.Admin;
import com.momentliving.entity.Merchant;
import com.momentliving.entity.MerchantApply;
import com.momentliving.exception.BadRequestException;
import com.momentliving.mapper.AdminMapper;
import com.momentliving.mapper.MerchantApplyMapper;
import com.momentliving.mapper.MerchantMapper;
import com.momentliving.result.Result;
import com.momentliving.service.MerchantApplyService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 商家入驻申请实现（merchant-service 侧）
 * 占用校验查 admin（管理员）+ merchant（已通过商家）两表——本服务与 admin-service 共用 momentliving 库，
 * 直接本地查询（审核通过时的最终占用校验在 admin-service 审计事务里兜底）
 */
@Slf4j
@Service
public class MerchantApplyServiceImpl implements MerchantApplyService {

    @Resource
    private MerchantApplyMapper merchantApplyMapper;
    @Resource
    private AdminMapper adminMapper;
    @Resource
    private MerchantMapper merchantMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Result<Long> apply(MerchantApplyDTO dto) {
        // 1. 基础校验
        if (StrUtil.hasBlank(dto.getShopName(), dto.getContactPhone(), dto.getUsername(), dto.getPassword())) {
            throw new BadRequestException("店铺名称、联系电话、账号、密码均为必填");
        }
        if (StrUtil.isBlank(dto.getUsername()) || dto.getUsername().length() < 4 || dto.getUsername().length() > 32) {
            throw new BadRequestException("账号长度需在 4~32 位之间");
        }
        if (dto.getPassword().length() < 6) {
            throw new BadRequestException("密码至少 6 位");
        }
        // 2. 账号占用校验：admin（管理员）与 merchant（已通过审核的商家）任一存在即不允许再申请
        if (adminMapper.selectCount(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getUsername, dto.getUsername())) > 0
                || merchantMapper.selectCount(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getUsername, dto.getUsername())) > 0) {
            throw new BadRequestException("该账号已被占用，请更换账号");
        }
        // 3. 同账号的历史申请：待审核 → 拒绝重复提交；已通过 → 提示直接登录；已拒绝 → 复用该行重新进队列
        MerchantApply exist = merchantApplyMapper.selectOne(new LambdaQueryWrapper<MerchantApply>()
                .eq(MerchantApply::getUsername, dto.getUsername()));
        String encodedPwd = passwordEncoder.encode(dto.getPassword());
        if (exist != null) {
            if (exist.getStatus() == MerchantApply.STATUS_PENDING) {
                throw new BadRequestException("该账号已有待审核的申请，请等待管理员审核");
            }
            if (exist.getStatus() == MerchantApply.STATUS_APPROVED) {
                throw new BadRequestException("该账号已通过审核，请直接登录");
            }
            exist.setShopName(dto.getShopName());
            exist.setAddress(dto.getAddress());
            exist.setContactPhone(dto.getContactPhone());
            exist.setPassword(encodedPwd);
            exist.setStatus(MerchantApply.STATUS_PENDING);
            exist.setRejectReason(null);
            exist.setAuditBy(null);
            exist.setAuditTime(null);
            merchantApplyMapper.updateById(exist);
            return Result.success(exist.getId());
        }
        // 4. 新申请落库（密码提交时即 BCrypt 加密，库里不存明文）
        MerchantApply apply = MerchantApply.builder()
                .shopName(dto.getShopName())
                .address(dto.getAddress())
                .contactPhone(dto.getContactPhone())
                .username(dto.getUsername())
                .password(encodedPwd)
                .status(MerchantApply.STATUS_PENDING)
                .build();
        merchantApplyMapper.insert(apply);
        log.info("商家入驻申请已提交 username={}, shopName={}", dto.getUsername(), dto.getShopName());
        return Result.success(apply.getId());
    }
}
