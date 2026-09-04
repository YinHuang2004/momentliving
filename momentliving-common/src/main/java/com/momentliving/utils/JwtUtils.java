package com.momentliving.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.momentliving.properties.JwtProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtils {

    public static String createAccessToken(Long userId,Long accessTokenTtl,String secret) {
        return JWT.create()
                .withClaim("userId", userId)
                .withExpiresAt(new Date(System.currentTimeMillis() + accessTokenTtl * 60 * 1000L))
                .sign(Algorithm.HMAC256(secret));
    }

    public static String createRefreshToken(Long userId,Long refreshTokenTtl,String secret) {
        return JWT.create()
                .withClaim("userId", userId)
                .withExpiresAt(new Date(System.currentTimeMillis() + refreshTokenTtl * 24 * 60 * 60 * 1000L))
                .sign(Algorithm.HMAC256(secret));
    }

    public static Long getUserId(String token,String secret) {
        DecodedJWT jwt = JWT.require(Algorithm.HMAC256(secret)).build().verify(token);
        return jwt.getClaim("userId").asLong();
    }
}
