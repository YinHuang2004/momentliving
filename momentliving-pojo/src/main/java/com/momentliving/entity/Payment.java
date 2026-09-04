package com.momentliving.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水表（支付对接）
 * 对应表：payment
 * 约定：一单一笔流水（uk_order_id）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务订单ID（voucher_order.id） */
    private Long orderId;

    /** 支付用户ID */
    private Long userId;

    /** 支付金额（元） */
    private BigDecimal amount;

    /** 支付方式：1微信 2支付宝 */
    private Integer payType;

    /** 状态：0待支付 1已支付 2已退款 3支付失败 */
    private Integer status;

    /** 第三方支付流水号 */
    private String transactionId;

    /** 支付回调原始报文（验签/对账用） */
    private String notifyContent;

    /** 支付成功时间 */
    private LocalDateTime payTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
