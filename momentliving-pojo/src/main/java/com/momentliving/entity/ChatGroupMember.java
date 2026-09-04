package com.momentliving.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 群成员
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("chat_group_member")
public class ChatGroupMember implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 群内角色：0成员 1群管理 2群主 */
    public static final int ROLE_MEMBER = 0;
    public static final int ROLE_ADMIN = 1;
    public static final int ROLE_OWNER = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;

    private Long userId;

    /** 群内昵称（可空=用用户昵称） */
    private String groupNickname;

    /** 0成员 1群管理 2群主 */
    private Integer role;

    private LocalDateTime joinTime;
}
