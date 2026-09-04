package com.momentliving.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 商家端工作台统计聚合结果（GET /admin/verify/stats）
 * 数据来源：voucher-service 同库统计 + admin-service 回填买家昵称
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MerchantStatsVO {
    /** 今日概览（待核销/已核销/营收） */
    private TodayStats today;
    /** 最近核销列表（按核销时间倒序，最多 10 条） */
    private List<RecentVerifyVO> recent;
}
