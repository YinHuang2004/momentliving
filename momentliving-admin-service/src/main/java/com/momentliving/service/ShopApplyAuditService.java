package com.momentliving.service;

import com.momentliving.dto.ShopApplyAuditDTO;
import com.momentliving.entity.ShopApply;
import com.momentliving.result.Result;

import java.util.List;

/**
 * 开店申请审核（admin-service 侧：平台管理员查看/审核）
 * 申请提交在 merchant-service（/merchant/shop/apply），两服务共读写 shop_apply 表。
 * 管理员不能直接新增店铺——这是店铺上线的唯一入口。
 */
public interface ShopApplyAuditService {

    /** 开店申请列表（仅平台管理员） */
    Result<List<ShopApply>> list(Integer status, Integer current, Integer pageSize);

    /** 审核：通过 → Seata 全局事务内经 shop-service 建店上线；拒绝 → 记录原因 */
    Result<Void> audit(ShopApplyAuditDTO dto);
}
