package com.momentliving.service;

/**
 * 商家 AI（B 端）：经营分析 / 营销文案 / 店铺介绍
 */
public interface AiMerchantService {

    /** 经营分析报告（数据来自 voucher-service/shop-service 实时接口） */
    String analysis();

    /** 优惠券营销文案 */
    String copywriting(String voucherDesc, String sellingPoint);

    /** 店铺介绍优化文案 */
    String shopIntro();
}
