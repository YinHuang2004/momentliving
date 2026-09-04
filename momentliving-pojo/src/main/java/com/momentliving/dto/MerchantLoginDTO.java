package com.momentliving.dto;

import lombok.Data;

/** 商家登录参数（/merchant/login） */
@Data
public class MerchantLoginDTO {
    private String username;
    private String password;
}
