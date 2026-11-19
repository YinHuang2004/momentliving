package com.momentliving.pay;

import com.momentliving.entity.VoucherOrder;
import com.momentliving.exception.PayException;

import java.math.BigDecimal;

/**
 * 支付渠道 SPI：当前提供支付宝沙箱与 Mock 实现，由 {@code PaymentServiceImpl} 按 payType 路由。
 * prepay 返回值约定见 {@link com.momentliving.vo.PayOrderVO#getPayContent()}。
 */
public interface PayProvider {

    /** 本实现支持的支付方式：2支付宝，3 Mock。保留历史编号，避免已有支付数据不兼容。 */
    int supportPayType();

    /**
     * 预下单：创建渠道支付单，返回支付页内容（form HTML / 收银台 URL）
     *
     * @param order   业务订单（status=待支付）
     * @param amount  实付金额（元）
     * @param subject 商品标题（展示在收银台）
     */
    String prepay(VoucherOrder order, BigDecimal amount, String subject);

    /**
     * 同步退款（仅限已支付未核销订单）。失败抛 {@link PayException}。
     */
    void refund(Long orderId, BigDecimal amount, String reason);
}
