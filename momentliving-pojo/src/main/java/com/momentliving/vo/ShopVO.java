package com.momentliving.vo;

import com.momentliving.entity.Shop;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商铺视图对象：Entity 基础上补充与当前用户的距离等展示字段
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShopVO extends Shop {

    /** 与当前用户的距离（米，GEO 查询时填充，非数据库字段） */
    private Double distance;
}
