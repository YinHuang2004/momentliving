package com.momentliving.service;

import com.momentliving.dto.MerchantApplyAuditDTO;
import com.momentliving.entity.MerchantApply;
import com.momentliving.result.Result;

import java.util.List;

/**
 * 商家入驻审核（admin-service 侧：平台管理员查看/审核申请）
 * 申请提交在 merchant-service（/merchant/apply），两服务共读写 merchant_apply 表
 */
public interface MerchantApplyService {

    /** 申请列表（仅平台管理员，password 脱敏） */
    Result<List<MerchantApply>> list(Integer status, Integer current, Integer pageSize);

    /** 审核：通过 → 事务内创建 shop + merchant 商家账号；拒绝 → 记录原因 */
    Result<Void> audit(MerchantApplyAuditDTO dto);
}
