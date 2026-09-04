package com.momentliving.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证码下发结果
 * code 为 null 表示验证码已通过其他渠道发送（如邮箱 SMTP），前端无需展示；
 * code 非空表示演示模式直接返回前端展示（手机号未接短信），expireSeconds 为有效期秒数。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CaptchaVO {
    /** 验证码（仅演示模式返回，如手机号登录） */
    private String code;
    /** 有效期秒数（前端展示过期倒计时用） */
    private Long expireSeconds;
}
