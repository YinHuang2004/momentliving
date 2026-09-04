package com.momentliving.pay;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.momentliving.config.PayProperties;
import com.momentliving.entity.VoucherOrder;
import com.momentliving.exception.PayException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付宝渠道实现（官方 SDK，沙箱/正式由 gateway 配置切换）
 *
 * <p>对接的接口：
 * <ul>
 *   <li>预下单：alipay.trade.page.pay（PC 页面支付，返回自动提交 form）</li>
 *   <li>异步通知：POST /pay/alipay/notify，RSA2 验签后处理</li>
 *   <li>退款：alipay.trade.refund（同步返回，退成功即成功）</li>
 * </ul>
 */
@Slf4j
@Component
public class AliPayProvider implements PayProvider {

    public static final int PAY_TYPE_ALIPAY = 2;

    @Resource
    private PayProperties payProperties;

    private AlipayClient client;
    private volatile boolean inited = false;

    /** 应用启动时初始化 AlipayClient（enabled=false 时不初始化，走 mock 演示） */
    @PostConstruct
    public void init() {
        PayProperties.Alipay conf = payProperties.getAlipay();
        if (!conf.isEnabled()) {
            log.info("支付宝渠道未启用（momentliving.pay.alipay.enabled=false），支付演示请开启 mock 渠道");
            return;
        }
        client = new DefaultAlipayClient(
                conf.getGateway(), conf.getAppId(), conf.getAppPrivateKey(),
                "json", conf.getCharset(), conf.getAlipayPublicKey(), conf.getSignType());
        inited = true;
        log.info("支付宝渠道初始化完成，gateway={}, appId={}", conf.getGateway(), mask(conf.getAppId()));
    }

    @Override
    public int supportPayType() {
        return PAY_TYPE_ALIPAY;
    }

    @Override
    public String prepay(VoucherOrder order, BigDecimal amount, String subject) {
        requireInited();
        PayProperties.Alipay conf = payProperties.getAlipay();
        try {
            AlipayTradePagePayModel model = new AlipayTradePagePayModel();
            // out_trade_no = 业务订单号；timeoutExpress 与订单超时关单保持一致（如 30m）
            model.setOutTradeNo(order.getId().toString());
            model.setTotalAmount(amount.toPlainString());
            model.setSubject(subject);
            model.setProductCode("FAST_INSTANT_TRADE_PAY");
            model.setTimeoutExpress(payProperties.getOrderTimeoutMinutes() + "m");

            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            request.setBizModel(model);
            request.setNotifyUrl(conf.getNotifyUrl());
            request.setReturnUrl(conf.getReturnUrl());

            AlipayTradePagePayResponse response = client.pageExecute(request);
            if (response == null || response.getBody() == null) {
                throw new PayException("支付宝预下单失败：" + (response == null ? "空响应" : response.getSubMsg()));
            }
            return response.getBody();
        } catch (AlipayApiException e) {
            log.error("支付宝预下单异常，orderId={}", order.getId(), e);
            throw new PayException("支付宝预下单失败，请稍后重试");
        }
    }

    /**
     * 异步回调验签 + 基础校验。返回 outTradeNo 与渠道流水号。
     * 只负责"验证这笔通知确实来自支付宝且金额正确"，状态流转交给 PaymentService。
     */
    public NotifyPayload verifyNotify(Map<String, String> params) throws AlipayApiException {
        PayProperties.Alipay conf = payProperties.getAlipay();
        requireInited();
        // 1. RSA2 验签：remove the sign & sign_type per official spec（rsaCheckV1 内部已剔除）
        boolean signOk = AlipaySignature.rsaCheckV1(params, conf.getAlipayPublicKey(), conf.getCharset(), conf.getSignType());
        if (!signOk) {
            throw new PayException("支付宝回调验签失败");
        }
        // 2. 校验 app_id（防止别的应用的通知打到我这）
        if (!conf.getAppId().equals(params.get("app_id"))) {
            throw new PayException("回调 app_id 不匹配");
        }
        // 3. 校验卖家账号（可选配置）
        if (conf.getSellerId() != null && !conf.getSellerId().isBlank()
                && !conf.getSellerId().equals(params.get("seller_id"))) {
            throw new PayException("回调 seller_id 不匹配");
        }
        // 4. 只有这两个状态代表用户真实付了钱
        String tradeStatus = params.get("trade_status");
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            throw new PayException("交易未成功，trade_status=" + tradeStatus);
        }
        return new NotifyPayload(Long.valueOf(params.get("out_trade_no")),
                params.get("trade_no"), new BigDecimal(params.get("total_amount")));
    }

    @Override
    public void refund(Long orderId, BigDecimal amount, String reason) {
        requireInited();
        try {
            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            request.setBizContent(String.format(
                    "{\"out_trade_no\":\"%s\",\"refund_amount\":\"%s\",\"refund_reason\":\"%s\"}",
                    orderId, amount.toPlainString(), reason == null ? "用户退款" : reason));
            AlipayTradeRefundResponse response = client.execute(request);
            if (response.isSuccess()) {
                log.info("支付宝退款成功，orderId={}, fundChange={}", orderId, response.getFundChange());
            } else {
                throw new PayException("支付宝退款失败：" + response.getSubMsg());
            }
        } catch (AlipayApiException e) {
            log.error("支付宝退款异常，orderId={}", orderId, e);
            throw new PayException("支付宝退款失败，请稍后重试");
        }
    }

    private void requireInited() {
        if (!inited) {
            throw new PayException("支付宝渠道未启用，请检查 momentliving.pay.alipay 配置或使用 mock 演示通道");
        }
    }

    private static String mask(String appId) {
        if (appId == null || appId.length() <= 8) {
            return "***";
        }
        return appId.substring(0, 4) + "****" + appId.substring(appId.length() - 4);
    }

    /** 异步通知载荷 */
    public record NotifyPayload(Long orderId, String tradeNo, BigDecimal totalAmount) {}
}
