package com.momentliving.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商家信息 VO（/merchant/login、/merchant/me 返回）
 * 与 AdminVO（平台管理员）区分：无 role（merchant 表全是商家），头像字段为 avatar
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MerchantVO {
    private Long id;
    private String username;
    private String name;
    private String phone;
    /** 绑定的店铺ID（工作台统计/我的店铺必用） */
    private Long shopId;
    private String avatar;
    /** 商家端 AccessToken（仅登录接口返回，前端存 storage 后放 Authorization 头） */
    private String token;
}
