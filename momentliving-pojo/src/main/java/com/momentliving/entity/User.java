package com.momentliving.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements Serializable {
    private static final long serialVersionUID = 1L;
    @TableId(type= IdType.AUTO)
    private Long id;
    private String phone;
    private String email;
    private String nickName;
    private String images;
    /**
     * 密码（BCrypt 哈希，60 字符）。
     * null = 未设置密码（纯验证码登录/注册的用户）；
     * 首次"设置密码"后才有值，之后修改密码需校验原密码。
     * ⚠️ UserVO 不含该字段，BeanUtils.copyProperties 不会把它带给前端。
     */
    private String password;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
