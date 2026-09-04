package com.momentliving.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 管理后台 Dashboard 数据（GET /admin/dashboard）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardVO {

    /** 店铺总数 */
    private Long shopCount;

    /** 优惠券总数（含秒杀） */
    private Long voucherCount;

    /** 待审核入驻申请数 */
    private Long pendingApplyCount;

    /** 商家账号数（merchant 表，status=1） */
    private Long merchantCount;

    /** 店铺分类分布（环形图）：name=分类名，count=店铺数 */
    private List<TypeCount> typeDistribution;

    /** 近 7 日入驻申请趋势（折线图）：date=MM-dd，count=申请数 */
    private List<DateCount> applyTrend;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypeCount {
        private String name;
        private Long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateCount {
        private String date;
        private Long count;
    }
}
