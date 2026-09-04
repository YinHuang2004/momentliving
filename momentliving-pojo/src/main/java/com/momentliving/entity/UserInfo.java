package com.momentliving.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class UserInfo implements Serializable {

    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long userId;
    private String city;
    private String introduce;
    private Integer fans;
    private Integer followee;
    private Boolean gender;
    private LocalDate birthday;
    private Integer credits;
    private Boolean level;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /** 昵称透传字段：昵称实际存 user 表（user_info 无此列），updateInfo 时由 service 摘出单独更新；查询时由 /user/info/{id} 回填 */
    @TableField(exist = false)
    private String nickName;
    /** 头像透传字段：头像同样存 user 表（user.images），/user/info/{id} 回填，个人主页展示用 */
    @TableField(exist = false)
    private String images;
}
