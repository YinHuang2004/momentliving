package com.momentliving.dto;

import lombok.Data;

/** 管理员登录入参 DTO:用户名 + 密码 */
@Data
public class AdminLoginDTO {
    private String username;
    private String password;
}
