package com.momentliving.dto;

import lombok.Data;

/**
 * 找回密码（重置密码）请求体（无需登录，POST /user/password/reset）
 * <p>流程：前端先调 POST /user/code?email=xxx 发送邮箱验证码 →
 * 提交本接口校验验证码 → 重置密码。
 * <p>重置成功后服务端会删除 Redis 中的 RefreshToken（login:refresh:{userId}），
 * 防止盗号者手里的旧 RefreshToken 继续换新 AccessToken——这是找回密码的核心安全动作。
 */
@Data
public class PasswordResetDTO {
    /** 注册邮箱 */
    private String email;
    /** 邮箱收到的 6 位验证码（2 分钟有效，用后即删） */
    private String code;
    /** 新密码（6~32 位） */
    private String newPassword;
}
