package com.momentliving.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.momentliving.api.client.ShopClient;
import com.momentliving.context.AdminHolder;
import com.momentliving.dto.MerchantApplyAuditDTO;
import com.momentliving.dto.MerchantApplyDTO;
import com.momentliving.dto.ShopDTO;
import com.momentliving.entity.Admin;
import com.momentliving.entity.Merchant;
import com.momentliving.entity.MerchantApply;
import com.momentliving.mapper.AdminMapper;
import com.momentliving.mapper.MerchantApplyMapper;
import com.momentliving.mapper.MerchantMapper;
import com.momentliving.exception.BadRequestException;
import com.momentliving.result.Result;
import com.momentliving.service.MerchantApplyService;
import io.seata.spring.annotation.GlobalTransactional;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商家入驻申请/审核实现
 *
 * <p>账号体系（已拆分）：商家账号在独立 merchant 表（商家端在 merchant-service），平台管理员在 admin 表。
 * 申请提交（/merchant/apply）在 merchant-service，这里只负责平台管理员审核；审核通过后：
 * <ul>
 *   <li>① Feign 调 shop-service 创建 shop 记录（跨服务分支事务）</li>
 *   <li>② 本服务创建 merchant 商家账号（shop_id=新店铺）+ 更新申请状态（本地分支事务）</li>
 * </ul>
 * ①②包在同一个 Seata 全局事务里（{@code @GlobalTransactional}）：
 * 任何一步失败（如账号冲突、下游异常），shop 与本地写入一起按 undo_log 回滚，
 * 不会出现"店开了但账号没建"或反之的中间态。
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
    @Resource
    private ShopClient shopClient;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Result<List<MerchantApply>> list(Integer status, Integer current, Integer pageSize) {
        checkAdmin();
        LambdaQueryWrapper<MerchantApply> wrapper = new LambdaQueryWrapper<MerchantApply>()
                .eq(status != null, MerchantApply::getStatus, status)
                .orderByDesc(MerchantApply::getCreateTime);
        Page<MerchantApply> page = merchantApplyMapper.selectPage(
                new Page<>(current == null ? 1 : current, pageSize == null ? 10 : pageSize), wrapper);
        page.getRecords().forEach(a -> a.setPassword(null));   // 脱敏
        return Result.success(page.getRecords());
    }

    @Override
    @GlobalTransactional(rollbackFor = Exception.class, name = "momentliving-merchant-audit")
    public Result<Void> audit(MerchantApplyAuditDTO dto) {
        // 1. 仅平台管理员可审核
        Admin actor = checkAdmin();
        if (dto.getId() == null || dto.getApproved() == null) {
            throw new BadRequestException("缺少申请ID或审核结论");
        }
        if (!dto.getApproved() && StrUtil.isBlank(dto.getReason())) {
            throw new BadRequestException("拒绝时必须填写原因");
        }
        // 2. 申请必须存在且处于待审核
        MerchantApply apply = merchantApplyMapper.selectById(dto.getId());
        if (apply == null) {
            throw new BadRequestException("申请不存在");
        }
        if (apply.getStatus() != MerchantApply.STATUS_PENDING) {
            throw new BadRequestException("该申请已处理过，请勿重复审核");
        }

        if (dto.getApproved()) {
            // 3a. 通过：Seata 全局事务里跨服务建店 + 本地建号
            // shop 表多列为 NOT NULL 无默认值（type_id/x/y/images/sold/comments/score），这里给开店铺默认值，
            // 商家登录后可在管理端补全（分类/坐标/图片）
            ShopDTO shop = new ShopDTO();
            shop.setName(apply.getShopName());
            shop.setAddress(StrUtil.blankToDefault(apply.getAddress(), ""));
            shop.setTypeId(1L);         // 默认"美食"分类
            shop.setImages("");         // NOT NULL 占位
            shop.setX(116.397428);      // 默认坐标（后续 GEO 搜索可用），商家补全
            shop.setY(39.90923);
            shop.setSold(0);
            shop.setComments(0);
            shop.setScore(0);
            // ① 跨服务分支：店铺创建下沉到 shop-service（XID 经 Feign 头透传，下游注册分支事务）
            Result<Long> created = shopClient.create(shop);
            if (created == null || created.getCode() != 1 || created.getData() == null) {
                // 调不通/下游拒绝 → 全局回滚（本地已写的也会被 undo_log 逆补偿）
                throw new IllegalStateException("店铺创建失败：" + (created == null ? "下游无响应" : created.getMsg()));
            }
            Long shopId = created.getData();
            // ② 本地分支：账号占用二次校验（申请提交后到审核前，账号可能被别人注册）——
            //    此处抛出异常会连带回滚上面已创建的 shop，这正是分布式事务要解决的问题
            if (adminMapper.selectCount(new LambdaQueryWrapper<Admin>()
                    .eq(Admin::getUsername, apply.getUsername())) > 0
                    || merchantMapper.selectCount(new LambdaQueryWrapper<Merchant>()
                    .eq(Merchant::getUsername, apply.getUsername())) > 0) {
                throw new BadRequestException("账号 " + apply.getUsername() + " 已被占用，请拒绝该申请让商家更换后重新提交");
            }
            // ③ 写入 merchant 商家账号表（账号体系已与 admin 表拆分）
            Merchant merchant = Merchant.builder()
                    .username(apply.getUsername())
                    .password(apply.getPassword())          // 申请时已 BCrypt 加密，直接沿用
                    .name(apply.getShopName())
                    .phone(apply.getContactPhone())
                    .shopId(shopId)
                    .status(1)
                    .build();
            merchantMapper.insert(merchant);
            apply.setStatus(MerchantApply.STATUS_APPROVED);
            log.info("入驻审核通过 applyId={}, Seata 建店 shopId={} + 商家账号 {}", apply.getId(), shopId, apply.getUsername());
        } else {
            // 3b. 拒绝：记录原因，商家可修改后重新提交
            apply.setStatus(MerchantApply.STATUS_REJECTED);
            apply.setRejectReason(dto.getReason());
            log.info("入驻审核拒绝 applyId={}, 原因: {}", apply.getId(), dto.getReason());
        }
        apply.setAuditBy(actor.getId());
        apply.setAuditTime(LocalDateTime.now());
        merchantApplyMapper.updateById(apply);

        // 🆕 回滚演练（教学用）：此时店铺与本地账号都已写入，抛异常验证 Seata 把两边一起回滚
        if (Boolean.TRUE.equals(dto.getDrillFail())) {
            throw new IllegalStateException("【演练】模拟审核后半段失败 → 触发 Seata 全局回滚（店铺/账号/申请状态均应还原）");
        }
        return Result.success();
    }

    /** 校验当前登录者是平台管理员并返回其账号信息（admin 表已只存平台管理员，无需 role 判断） */
    private Admin checkAdmin() {
        com.momentliving.vo.AdminVO holder = AdminHolder.getAdmin();
        if (holder == null) {
            throw new BadRequestException("未登录");
        }
        // 拦截器只塞了 id，账号以 DB 为准（防止 token 未过期但账号已被删除/禁用）
        Admin admin = adminMapper.selectById(holder.getId());
        if (admin == null) {
            throw new BadRequestException("仅平台管理员可执行该操作");
        }
        return admin;
    }
}
