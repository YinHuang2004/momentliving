package com.momentliving.dto;

import lombok.Data;

/**
 * 修改密码请求体（已登录用户，PUT /user/password）
 * <p>语义：
 * <ul>
 *   <li>账号已设置过密码 → oldPassword 必填，校验通过才允许改；</li>
 *   <li>账号未设置过密码（纯验证码注册）→ oldPassword 可不传，本次为"首次设置密码"。</li>
 * </ul>
 * 修改成功后服务端会删除 Redis 中的 RefreshToken（login:refresh:{userId}），
 * 该账号所有设备的登录态立即失效，需用新密码重新登录。
 */
@Data
public class PasswordUpdateDTO {
    /** 原密码（未设置过密码时可不传） */
    private String oldPassword;
    /** 新密码（6~32 位） */
    private String newPassword;
}
