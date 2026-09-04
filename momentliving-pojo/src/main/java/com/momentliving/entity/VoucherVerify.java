package com.momentliving.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券核销记录表
 * 对应表：voucher_verify
 * 约定：核销码下单时生成、Redis 预热，商家扫码核销
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherVerify implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 券订单ID（voucher_order.id） */
    private Long orderId;

    /** 券所属用户ID */
    private Long userId;

    /** 核销店铺ID */
    private Long shopId;

    /** 核销码（全局唯一） */
    private String verifyCode;

    /** 核销人（employee.id） */
    private Long verifyBy;

    /** 状态：0未核销 1已核销 2已作废 */
    private Integer status;

    /** 核销时间 */
    private LocalDateTime verifyTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
