package com.momentliving.pay;

import com.momentliving.entity.VoucherOrder;
import com.momentliving.exception.PayException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 微信支付渠道（预留位）。
 *
 * <p>当前项目没有微信商户号，无法完成 JSAPI 支付必需的商户参数（mchid、商户私钥、
 * APIv3 密钥、证书序列号），且 JSAPI 还需要公众号 openid —— 真实联调门槛很高。
 * 这里保留与支付宝一致的 SPI 形态：
 * 状态机流转逻辑（PaymentService.processPaid / refund）与渠道无关，
 * 拿到商户号后补全 prepay/refund 即可接入真实微信支付。
 */
@Slf4j
@Component
public class WechatPayProvider implements PayProvider {

    public static final int PAY_TYPE_WECHAT = 1;

    @Override
    public int supportPayType() {
        return PAY_TYPE_WECHAT;
    }

    @Override
    public String prepay(VoucherOrder order, BigDecimal amount, String subject) {
        // 拿到商户号后的接入路径：Native 下单（wechatpay-java 的 NativePayService）
        // 生成 code_url → 前端展示二维码；回调通知 POST /pay/wechat/notify 走同一 processPaid
        throw new PayException("微信支付商户号尚未配置，请使用支付宝沙箱或 mock 演示通道");
    }

    @Override
    public void refund(Long orderId, BigDecimal amount, String reason) {
        throw new PayException("微信支付商户号尚未配置，退款不可用");
    }
}
