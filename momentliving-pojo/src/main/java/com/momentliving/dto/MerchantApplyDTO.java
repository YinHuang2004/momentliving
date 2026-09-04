package com.momentliving.dto;

import lombok.Data;

/**
 * 商家入驻申请入参（公开接口，无需登录）
 */
@Data
public class MerchantApplyDTO {

    /** 申请入驻的店铺名称 */
    private String shopName;

    /** 店铺地址 */
    private String address;

    /** 联系电话 */
    private String contactPhone;

    /** 拟用商家账号（审核通过即登录账号，全局唯一） */
    private String username;

    /** 登录密码（明文提交，服务端 BCrypt 加密存储） */
    private String password;
}
