package com.momentliving.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.*;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 用户关注关系实体(表 follow):userId 关注 followUserId */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Follow implements Serializable {

    private static final long serialVersionUID = 1L;
    @TableId(type =IdType.AUTO)
    private Long id;
    private Long userId;
    private Long followUserId;
    private LocalDateTime createTime;
}
