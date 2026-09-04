package com.momentliving.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 群成员 VO：成员信息 + 角色 + 用户资料（Feign 回填）
 */
@Data
public class GroupMemberVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    /** 群内昵称（可空=用用户昵称） */
    private String groupNickname;

    /** 用户昵称 */
    private String nickName;

    /** 用户头像 */
    private String images;

    /** 0成员 1群管理 2群主 */
    private Integer role;

    private LocalDateTime joinTime;
}
