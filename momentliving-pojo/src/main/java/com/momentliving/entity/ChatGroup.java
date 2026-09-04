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
 * 群聊
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("chat_group")
public class ChatGroup implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String groupName;

    private Long ownerId;

    private Integer memberCount;

    private LocalDateTime createTime;
}
