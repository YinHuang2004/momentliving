package com.momentliving.ai.dto;

/**
 * 商家券文案生成请求。
 */
public record CopywritingRequest(
        String voucherDesc,
        String sellingPoint) {
}
