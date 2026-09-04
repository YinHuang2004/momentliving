package com.momentliving.pay;

import com.momentliving.config.PayProperties;
import com.momentliving.entity.VoucherOrder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * mock 支付渠道：仅当 momentliving.pay.mock-enabled=true 时注册（@ConditionalOnProperty 控制）。
 *
 * <p>用途：本地开发环境没有公网地址，收不到支付宝沙箱的异步回调；
 * mock 渠道返回一个演示页，点"模拟支付成功"后请求 {@code /pay/mock/notify}，
 * 进入与真实渠道完全相同的幂等状态机处理流程，保证核心链路可完整闭环演示。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "momentliving.pay", name = "mock-enabled", havingValue = "true")
public class MockPayProvider implements PayProvider {

    public static final int PAY_TYPE_MOCK = 3;

    @Resource
    private PayProperties payProperties;

    @Override
    public int supportPayType() {
        return PAY_TYPE_MOCK;
    }

    @Override
    public String prepay(VoucherOrder order, BigDecimal amount, String subject) {
        log.warn("使用 mock 支付渠道（momentliving.pay.mock-enabled=true），生产环境必须关闭！orderId={}", order.getId());
        return """
                <!DOCTYPE html>
                <html lang="zh">
                <head><meta charset="UTF-8"><title>mock 收银台</title></head>
                <body style="text-align:center;margin-top:80px;font-family:sans-serif">
                  <h3>【mock 演示】第三方收银台</h3>
                  <p>订单号：%s</p>
                  <p>商品：%s</p>
                  <p>金额：￥%s</p>
                  <button onclick="fetch('%s?orderId=%s&amount=%s',{method:'POST'})
                    .then(r=>r.text()).then(t=>alert(t==='success'?'支付成功':'支付失败'))
                    .catch(e=>alert('请求失败:'+e))" style="padding:12px 48px">模拟支付成功</button>
                </body></html>
                """.formatted(order.getId(), subject, amount.toPlainString(),
                payProperties.getMockCallbackUrl(), order.getId(), amount.toPlainString());
    }

    @Override
    public void refund(Long orderId, BigDecimal amount, String reason) {
        log.warn("mock 渠道退款直接成功（不走第三方），orderId={}, amount={}", orderId, amount);
    }
}
