package com.momentliving.dto;

import lombok.Data;

/**
 * 商家开店申请入参（商家端提交，POST /merchant/shop/apply）
 */
@Data
public class ShopApplyDTO {

    /** 拟开店名称（必填） */
    private String shopName;

    /** 店铺分类（shop_type.id，不传默认 1=美食） */
    private Long typeId;

    /** 店铺地址 */
    private String address;

    /** 联系电话 */
    private String contactPhone;
}
