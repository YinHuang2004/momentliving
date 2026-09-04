package com.momentliving.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 店铺精简信息 VO（两处共用）：
 * 1. 优惠券详情页"适用店铺"（voucher-service MyBatis 映射，id/name/images/address 全字段）；
 * 2. 博客"可发布店铺"下拉（blog-service，只填 id/name，构造器 {@link #ShopSimpleVO(Long, String)}）。
 */
@Data
public class ShopSimpleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    /** 头图（多图逗号分隔，前端取第一张；博客场景为 null） */
    private String images;
    /** 地址（博客场景为 null） */
    private String address;

    public ShopSimpleVO() {
    }

    /** 博客"可发布店铺"场景：仅 id + name */
    public ShopSimpleVO(Long id, String name) {
        this.id = id;
        this.name = name;
    }
}
