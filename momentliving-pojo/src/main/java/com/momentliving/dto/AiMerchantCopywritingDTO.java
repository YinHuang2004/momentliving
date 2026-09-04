package com.momentliving.dto;

import lombok.Data;

/**
 * AI 营销文案生成入参 DTO（POST /ai/merchant/copywriting，商家态）
 */
@Data
public class AiMerchantCopywritingDTO {

    /** 券类型描述，如"满100减30团购券"（必填） */
    private String voucherDesc;

    /** 核心卖点，如"招牌双人套餐"（可选） */
    private String sellingPoint;
}
