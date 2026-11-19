package com.momentliving.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.momentliving.properties.JwtProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
/**
 * JWT 工具类:生成/解析 accessToken 与 refreshToken,
 * 供各服务登录签发 Token、拦截器解析用户身份使用。
 */
public class JwtUtils {

    /** 签发 accessToken,载入 userId,有效期按分钟计 */
    public static String createAccessToken(Long userId,Long accessTokenTtl,String secret) {
        return JWT.create()
                .withClaim("userId", userId)
                .withExpiresAt(new Date(System.currentTimeMillis() + accessTokenTtl * 60 * 1000L))
                .sign(Algorithm.HMAC256(secret));
    }

    /** 签发 refreshToken,有效期按天计,用于换取新的 accessToken */
    public static String createRefreshToken(Long userId,Long refreshTokenTtl,String secret) {
        return JWT.create()
                .withClaim("userId", userId)
                .withExpiresAt(new Date(System.currentTimeMillis() + refreshTokenTtl * 24 * 60 * 60 * 1000L))
                .sign(Algorithm.HMAC256(secret));
    }

    /** 校验并解析 Token,取出 userId(签名或过期不合法会抛异常) */
    public static Long getUserId(String token,String secret) {
        DecodedJWT jwt = JWT.require(Algorithm.HMAC256(secret)).build().verify(token);
        return jwt.getClaim("userId").asLong();
    }
}
