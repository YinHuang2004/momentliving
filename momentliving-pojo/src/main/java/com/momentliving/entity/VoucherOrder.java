package com.momentliving.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.*;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 券订单实体(表 voucher_order):status 状态机见 common 的 OrderStatus 常量 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VoucherOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long voucherId;
    /** 秒杀购买槽位（1..限购数）；普通券订单为空，由 DB 唯一约束兜底限购。 */
    private Integer purchaseSlot;
    private Integer payType;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime useTime;
    private LocalDateTime refundTime;
    private LocalDateTime updateTime;

    /** 16 位核销码（非库字段，查询时从 voucher_verify 表回填，用户出示给商家核销） */
    @TableField(exist = false)
    private String verifyCode;
}
