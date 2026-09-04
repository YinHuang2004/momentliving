package com.momentliving.dto;

import lombok.Data;

@Data
public class LoginFormDTO {
    private String email;
    /** 手机号（与 email 二选一，phone 优先） */
    private String phone;
    private String code;
}
