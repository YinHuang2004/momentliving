package com.momentliving.service;

import com.momentliving.dto.MerchantApplyDTO;
import com.momentliving.result.Result;

/**
 * 商家入驻申请（merchant-service 侧：商家提交申请）
 * 审核在 admin-service（平台管理员），两服务共读写 merchant_apply 表
 */
public interface MerchantApplyService {

    /** 提交入驻申请（公开，密码 BCrypt 加密落库） */
    Result<Long> apply(MerchantApplyDTO dto);
}
