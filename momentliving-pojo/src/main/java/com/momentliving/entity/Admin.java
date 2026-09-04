package com.momentliving.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 平台管理员（管理后台）
 * 对应表：admin
 * 职责：商家入驻审核、店铺上/下架、发券、数据看板
 * 约定：password 用 BCrypt 加密存储；与管理端 JWT 配套（/admin/** 独立鉴权，login:admin:{id}）
 * 注意：商家账号已拆分到独立 merchant 表（/merchant/**，login:merchant:{id}），本表不再存商家
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Admin implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录用户名（唯一） */
    private String username;

    /** 密码（BCrypt 加密） */
    private String password;

    /** 姓名 */
    private String name;

    /** 手机号 */
    private String phone;

    /** 状态：1正常 0已禁用 */
    private Integer status;

    /** 头像URL */
    private String images;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
