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
 * 商家账号（区别于平台管理员 admin，账号体系已拆分）
 * 对应表：merchant
 * 职责：到店核销、店铺经营（我的店铺编辑）；入驻审核通过后由 admin-service 写入本表
 * 约定：password 用 BCrypt 加密存储；登录态存 login:merchant:{id}（/merchant/** 独立鉴权）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Merchant implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录用户名（唯一） */
    private String username;

    /** 密码（BCrypt 加密） */
    private String password;

    /** 商家姓名/店长 */
    private String name;

    /** 手机号 */
    private String phone;

    /** 绑定的店铺ID（必填：我的店铺、工作台统计都按它归属） */
    private Long shopId;

    /** 头像URL */
    private String avatar;

    /** 状态：1正常 0已禁用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
