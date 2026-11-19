package com.momentliving.security;

import com.momentliving.properties.InternalAuthProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.function.Function;

@Component
/**
 * 内部请求验签器:各服务拦截器调用,对带内部身份头的请求校验签名,
 * 防止外部直接伪造 X-User-Id 等身份头越权调用。
 */
public class InternalRequestVerifier {
    private final InternalAuthProperties properties;

    public InternalRequestVerifier(InternalAuthProperties properties) {
        this.properties = properties;
    }

    /** 判断请求是否需要验签:携带了内部身份头,或访问 /internal/ 接口 */
    public boolean requiresVerification(String path, Function<String, String> header) {
        return hasText(header.apply(InternalRequestSigner.USER_ID))
                || hasText(header.apply(InternalRequestSigner.ADMIN_ID))
                || hasText(header.apply(InternalRequestSigner.MERCHANT_ID))
                || hasText(header.apply(InternalRequestSigner.MERCHANT_SHOP_ID))
                || path.startsWith("/internal/") || path.contains("/internal/");
    }

    /** 校验时间戳时钟偏移并重算签名比对;鉴权未启用时直接放行 */
    public boolean verify(String method, String path, Function<String, String> header) {
        if (!properties.isEnabled()) {
            return true;
        }
        String timestamp = header.apply(InternalRequestSigner.TIMESTAMP);
        String nonce = header.apply(InternalRequestSigner.NONCE);
        String signature = header.apply(InternalRequestSigner.SIGNATURE);
        if (!hasText(timestamp) || !hasText(nonce) || !hasText(signature)) {
            return false;
        }
        try {
            long requestEpoch = Long.parseLong(timestamp);
            long skew = Math.abs(Instant.now().getEpochSecond() - requestEpoch);
            if (skew > properties.getMaxClockSkewSeconds()) {
                return false;
            }
            String expected = InternalRequestSigner.sign(properties.requiredSecret(), method, path,
                    timestamp, nonce,
                    header.apply(InternalRequestSigner.USER_ID),
                    header.apply(InternalRequestSigner.ADMIN_ID),
                    header.apply(InternalRequestSigner.MERCHANT_ID),
                    header.apply(InternalRequestSigner.MERCHANT_SHOP_ID));
            return InternalRequestSigner.constantTimeEquals(expected, signature);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
