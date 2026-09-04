package com.momentliving.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付对接配置（momentliving.pay.*）
 * 敏感参数一律放 Nacos 共享配置，仓库模板只留占位符。
 *
 * <p>典型配置：
 * <pre>
 * momentliving:
 *   pay:
 *     order-timeout-minutes: 30          # 订单超时关闭时间（分钟），同时用于 MQ TTL 与兜底扫描
 *     mock-enabled: false                # 本地演示开关：true 时走 mock 支付页 + mock 回调通道
 *     mock-callback-url: http://localhost:8080/pay/mock/notify
 *     alipay:
 *       enabled: true                    # 未开通沙箱时置 false，仅用 mock 演示
 *       gateway: https://openapi-sandbox.dl.alipaydev.com/gateway.do
 *       app-id: ...                      # 沙箱 APPID
 *       app-private-key: ...             # 应用私钥（PKCS8）
 *       alipay-public-key: ...           # 支付宝公钥（验签用）
 *       notify-url: https://your-domain/pay/alipay/notify   # 需公网可达（内网穿透）
 *       return-url: https://your-domain/pay/alipay/return   # 同步跳转地址
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "momentliving.pay")
public class PayProperties {

    /** 订单支付超时时间（分钟）：MQ 延迟关单 TTL 与定时扫描共用同一来源 */
    private int orderTimeoutMinutes = 30;

    /** mock 支付开关：本地无沙箱环境时用，生产必须为 false */
    private boolean mockEnabled = false;

    /** mock 支付页里回调接口的完整地址（默认走网关） */
    private String mockCallbackUrl = "http://localhost:8080/pay/mock/notify";

    private Alipay alipay = new Alipay();

    @Data
    public static class Alipay {
        /** 是否启用支付宝渠道（未申请沙箱时关闭，避免 SDK 报无效网关） */
        private boolean enabled = false;
        /** 支付宝网关：沙箱 https://openapi-sandbox.dl.alipaydev.com/gateway.do，正式 https://openapi.alipay.com/gateway.do */
        private String gateway;
        private String appId;
        /** 应用私钥（PKCS8 格式，不带头尾标记的纯文本） */
        private String appPrivateKey;
        /** 支付宝公钥（异步回调 RSA2 验签用） */
        private String alipayPublicKey;
        /** 异步通知地址，必须公网可直接访问 */
        private String notifyUrl;
        /** 支付完成同步跳转地址（前端页面） */
        private String returnUrl;
        /** 签名类型，固定 RSA2 */
        private String signType = "RSA2";
        /** 编码，固定 UTF-8 */
        private String charset = "UTF-8";
        /** 商家账号 ID（卖家 PID，回调时校验 seller_id，防串单；沙箱可不填） */
        private String sellerId;
    }
}
