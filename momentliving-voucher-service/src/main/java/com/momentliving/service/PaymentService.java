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

    /** 支付状态查询：订单状态 + 流水状态（前端轮询用） */
    Result<Map<String, Object>> queryPayStatus(Long orderId);

    /** 支付宝回调验签后的载荷 */
    record AliNotifyPayload(Long orderId, String tradeNo, java.math.BigDecimal totalAmount) {}
}
