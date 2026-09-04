package com.momentliving.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.momentliving.dto.ShopApplyDTO;
import com.momentliving.entity.ShopApply;
import com.momentliving.exception.BadRequestException;
import com.momentliving.mapper.ShopApplyMapper;
import com.momentliving.result.Result;
import com.momentliving.service.MerchantShopApplyService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 开店申请（商家端）实现。
 * 规则：管理员不能直接新增店铺；店铺上线只有一条路 —— 商家在这里提交申请，
 * 平台管理员在 admin-service 审核通过后经 Seata 全局事务建店（见 ShopApplyAuditServiceImpl）。
 */
@Slf4j
@Service
public class MerchantShopApplyServiceImpl implements MerchantShopApplyService {

    @Resource
    private ShopApplyMapper shopApplyMapper;

    @Override
    public Result<Long> submit(Long merchantId, ShopApplyDTO dto) {
        // 1. 基础校验
        if (dto == null || StrUtil.hasBlank(dto.getShopName())) {
            throw new BadRequestException("店铺名称为必填项");
        }
        if (dto.getShopName().length() > 32) {
            throw new BadRequestException("店铺名称不能超过 32 字");
        }
        // 2. 同一商家已有待审核申请时拒绝重复提交（避免审核队列被刷）
        Long pending = shopApplyMapper.selectCount(new LambdaQueryWrapper<ShopApply>()
                .eq(ShopApply::getMerchantId, merchantId)
                .eq(ShopApply::getStatus, ShopApply.STATUS_PENDING));
        if (pending > 0) {
            throw new BadRequestException("您已有一条待审核的开店申请，请等待平台审核");
        }
        // 3. 落库（审核通过才会真正建店上线）
        ShopApply apply = ShopApply.builder()
                .merchantId(merchantId)
                .shopName(dto.getShopName().trim())
                .typeId(dto.getTypeId() == null ? 1L : dto.getTypeId())
                .address(StrUtil.blankToDefault(dto.getAddress(), ""))
                .contactPhone(StrUtil.blankToDefault(dto.getContactPhone(), ""))
                .status(ShopApply.STATUS_PENDING)
                .build();
        shopApplyMapper.insert(apply);
        log.info("开店申请已提交 merchantId={}, shopName={}, applyId={}", merchantId, apply.getShopName(), apply.getId());
        return Result.success(apply.getId());
    }

    @Override
    public Result<List<ShopApply>> listMine(Long merchantId) {
        return Result.success(shopApplyMapper.selectList(new LambdaQueryWrapper<ShopApply>()
                .eq(ShopApply::getMerchantId, merchantId)
                .orderByDesc(ShopApply::getCreateTime)));
    }
}
