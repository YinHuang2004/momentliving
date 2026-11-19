package com.momentliving.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "momentliving.internal-auth")
/**
 * 服务间内部调用鉴权配置(前缀 momentliving.internal-auth):
 * 鉴权开关、签名密钥、允许的最大时钟偏移。
 */
public class InternalAuthProperties {
    private boolean enabled = true;
    private String secret;
    private long maxClockSkewSeconds = 60;

    /**
     * 获取签名密钥;未启用鉴权返回空串,密钥未配置或不足 32 位直接抛异常
     */
    public String requiredSecret() {
        if (!enabled) {
            return "";
        }
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("momentliving.internal-auth.secret 必须通过环境/Nacos 配置且不少于 32 位");
        }
        return secret;
    }
}
