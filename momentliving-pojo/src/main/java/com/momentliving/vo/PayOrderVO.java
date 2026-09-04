package com.momentliving.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 发起支付返回 VO
 * payContent 说明：
 * - 支付宝（page.pay）：返回自动提交的 form HTML，前端直接写入页面即可跳转收银台
 * - mock 渠道：返回一个内嵌"确认支付"按钮的演示页，按钮触发 mock 回调走真实回调处理逻辑
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long orderId;

    /** 支付方式：1微信 2支付宝 3mock 演示 */
    private Integer payType;

    /** 订单状态（OrderStatus 常量） */
    private Integer orderStatus;

    /** 应付金额（元） */
    private BigDecimal amount;

    /** 支付页内容：form HTML 或跳转 URL */
    private String payContent;
}
