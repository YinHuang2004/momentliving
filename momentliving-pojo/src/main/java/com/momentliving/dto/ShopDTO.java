package com.momentliving.dto;

import lombok.Data;

/**
 * 店铺写操作入参 DTO（新增店铺 / 更新店铺共用）
 */
@Data
public class ShopDTO {

    /** 店铺 ID（更新时必填） */
    private Long id;
    private String name;
    private Long typeId;
    private String images;
    private String area;
    private String address;
    private Double x;
    private Double y;
    private Long avgPrice;
    private Integer sold;
    private Integer comments;
    private Integer score;
    private String openHours;
}
