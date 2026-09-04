package com.momentliving.dto;

import lombok.Data;

/**
 * 店铺分页查询入参 DTO（GET query 绑定）：按类型 /of/type 或按名称 /of/name
 */
@Data
public class ShopQueryDTO {

    /** 店铺类型 ID（/of/type 查询用） */
    private Integer typeId;

    /** 店铺名称关键词（/of/name 查询用，模糊匹配） */
    private String name;

    /** 页码（默认 1） */
    private Integer current = 1;

    /** 经度（可选；与 y 同时提供时按距离排序） */
    private Double x;

    /** 纬度（可选；与 x 同时提供时按距离排序） */
    private Double y;
}
