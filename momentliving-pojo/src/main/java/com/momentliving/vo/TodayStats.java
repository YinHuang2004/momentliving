package com.momentliving.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 商家端工作台"今日概览"统计
 * 口径：按核销店铺 shopId 统计
 * - pendingVerify：已支付(1) 但未核销的订单数（voucher_order JOIN voucher）
 * - verified：今日核销成功数（voucher_verify.status=1 且 verify_time 在今天）
 * - revenue：今日核销营收（核销成功的券 payValue 之和，单位：元）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TodayStats {
    /** 待核销订单数 */
    private Long pendingVerify;
    /** 今日已核销数 */
    private Long verified;
    /** 今日营收（元，保留 2 位小数） */
    private BigDecimal revenue;
}
