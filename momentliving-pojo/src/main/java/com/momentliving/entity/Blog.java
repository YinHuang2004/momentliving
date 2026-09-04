package com.momentliving.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class Blog implements Serializable {

    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private Long userId;
    private String title;
    private String images;
    private String content;
    private Integer liked;
    private Integer comments;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
