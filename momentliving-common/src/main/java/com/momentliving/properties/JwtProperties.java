package com.momentliving.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "momentliving.jwt")
@Data
/** JWT 配置属性(前缀 momentliving.jwt):签名密钥、双 Token 有效期、请求头名称 */
public class JwtProperties {

    /**
     * JWT 密钥（用于签名和验证）
     */
    private String secret;

    /**
     * Access Token 有效期（单位：分钟）
     */
    private Long accessTokenTtl;

    /**
     * Refresh Token 有效期（单位：天）
     */
    private Long refreshTokenTtl;

    /**
     * Token 在请求头中的名称（如：Authorization）
     */
    private String tokenName;
}