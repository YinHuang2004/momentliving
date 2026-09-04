package com.momentliving.dto;

import lombok.Data;

/**
 * 商家修改个人信息入参（PUT /merchant/me，需登录）
 * name/phone/avatar 为空则不更新；改密码时 oldPassword/newPassword 必须同时提供
 */
@Data
public class MerchantUpdateDTO {

    /** 商家姓名/店长 */
    private String name;

    /** 手机号 */
    private String phone;

    /** 头像URL */
    private String avatar;

    /** 旧密码（仅修改密码时必填，明文提交，服务端 BCrypt 校验） */
    private String oldPassword;

    /** 新密码（与 oldPassword 同时提供才触发改密） */
    private String newPassword;
}
