package com.momentliving.vo;

import com.momentliving.entity.Voucher;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券视图对象：Entity 基础上补充秒杀券的库存与活动时间（来自 seckill_voucher）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class VoucherVO extends Voucher {

    /** 秒杀库存（非数据库字段） */
    private Integer stock;
    /** 秒杀开始时间（非数据库字段） */
    private LocalDateTime beginTime;
    /** 秒杀结束时间（非数据库字段） */
    private LocalDateTime endTime;
    /** 🆕 适用店铺ID列表（范围唯一事实源 = voucher_shop：1 条=单店，N 条=多店，空=全场通用） */
    private List<Long> shopIds;
    /** 🆕 适用店铺精简信息（券详情页展示；全场通用时为空列表，前端显示"全场通用"横幅） */
    private List<ShopSimpleVO> scopeShops;
}
