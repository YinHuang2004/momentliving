package com.momentliving.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 内部请求签名工具:把 HTTP 方法、路径、时间戳、nonce 及身份头(X-User-Id 等)
 * 拼成规范化串后做 HmacSHA256 签名,Feign 拦截器(签名方)与服务端验签器共用。
 */
public final class InternalRequestSigner {
    public static final String USER_ID = "X-User-Id";
    public static final String ADMIN_ID = "X-Admin-Id";
    public static final String MERCHANT_ID = "X-Merchant-Id";
    public static final String MERCHANT_SHOP_ID = "X-Merchant-Shop-Id";
    public static final String TIMESTAMP = "X-Internal-Timestamp";
    public static final String NONCE = "X-Internal-Nonce";
    public static final String SIGNATURE = "X-Internal-Signature";

    private InternalRequestSigner() {
    }

    /** 规范化串以换行拼接各要素,经 HmacSHA256 生成 URL-safe Base64 签名 */
    public static String sign(String secret, String method, String path, String timestamp, String nonce,
                              String userId, String adminId, String merchantId, String merchantShopId) {
        String canonical = value(method).toUpperCase() + '\n' + value(path) + '\n' + value(timestamp) + '\n'
                + value(nonce) + '\n' + value(userId) + '\n' + value(adminId) + '\n'
                + value(merchantId) + '\n' + value(merchantShopId);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("内部请求签名失败", e);
        }
    }

    /** 常量时间比较签名,防止时序攻击 */
    public static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
