package com.momentliving.service;

import com.momentliving.result.Result;
import com.momentliving.vo.PayOrderVO;

import java.util.Map;

/**
 * 订单支付服务：
 * 发起支付 → 渠道预下单 → 异步回调验签/幂等处理 → 状态机流转（配合 {@link com.momentliving.constant.OrderStatus}）
 */
public interface PaymentService {

    /**
     * 用户发起支付：校验订单归属与状态，创建/复用支付流水，调用渠道预下单。
     *
     * @return 支付页内容（支付宝 form HTML / mock 演示页）
     */
    Result<PayOrderVO> createPayment(Long orderId, Integer payType);

    /**
     * 第三方异步回调统一入口（支付宝真实回调、mock 回调都走这里）。
     * 幂等保证：payment uk_order_id 唯一约束 + 流水 CAS + 订单 CAS。
     *
     * @return true 表示该通知已被确认成功（渠道收到此结果即停止重试）
     */
    boolean processPaid(Long orderId, String transactionId, String rawNotify);

    /** 支付宝异步通知验签 + 状态校验，返回解析后的业务载荷 */
    AliNotifyPayload handleAlipayNotify(Map<String, String> params);

    /** 用户申请退款（仅限「已支付未核销」订单） */
    Result<Void> refund(Long orderId);

    /**
     * 用户取消支付（支付确认页点 X / 返回离开时调用）：
     * 仅将「待支付」流水置为支付失败，订单保持待支付，30 分钟内可重新发起支付；
     * 不动订单状态与秒杀库存 —— 若一直未支付，由 TTL 死信 + 定时扫描双保险自动关单回补。
     */
    Result<Void> cancelPayment(Long orderId);

    /** 支付状态查询：订单状态 + 流水状态（前端轮询用） */
    Result<Map<String, Object>> queryPayStatus(Long orderId);

    /** 支付宝回调验签后的载荷 */
    record AliNotifyPayload(Long orderId, String tradeNo, java.math.BigDecimal totalAmount) {}
}
