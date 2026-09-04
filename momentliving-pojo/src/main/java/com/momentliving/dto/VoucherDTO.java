package com.momentliving.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券写操作入参 DTO（新增普通券 / 新增秒杀券 / 更新券共用）
 * 秒杀券字段（stock/beginTime/endTime）仅新增秒杀券与更新时使用
 *
 * <p>适用范围（唯一事实源 = voucher_shop 表，多对多）：
 * <ul>
 *   <li>allShop=true            → 全场通用（不写关系记录）</li>
 *   <li>shopIds=[...]           → 指定店铺（1 条=单店券，N 条=多店券/批量发放）</li>
 * </ul>
 */
@Data
public class VoucherDTO {

    /** 优惠券 ID（更新时必填） */
    private Long id;
    private String title;
    private String subTitle;
    private String rules;
    /** 支付金额（分） */
    private Long payValue;
    /** 抵扣金额（分） */
    private Long actualValue;
    /** 券类型：0 普通券 1 秒杀券 */
    private Integer type;
    private Integer status;

    /** 🆕 是否全场通用券（true 时忽略 shopIds；适用店铺 = shopIds，1 条即单店券） */
    private Boolean allShop;
    /** 🆕 指定适用店铺列表（1 条=单店券，N 条=多店券/批量发放；与 allShop 互斥） */
    private List<Long> shopIds;

    /** 秒杀库存 */
    private Integer stock;
    /** 秒杀开始时间 */
    private LocalDateTime beginTime;
    /** 秒杀结束时间 */
    private LocalDateTime endTime;
}
