package com.momentliving.dto;

import lombok.Data;

/** 用户登录入参 DTO:邮箱或手机号 + 验证码 */
@Data
public class LoginFormDTO {
    private String email;
    /** 手机号（与 email 二选一，phone 优先） */
    private String phone;
    private String code;
}
