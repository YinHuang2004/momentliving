package com.momentliving.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminVO {
    private Long id;
    private String username;
    private String name;
    private String phone;
    private String images;
    /** 管理端 AccessToken（仅登录接口返回，前端存 storage 后放 Authorization 头） */
    private String token;
}
