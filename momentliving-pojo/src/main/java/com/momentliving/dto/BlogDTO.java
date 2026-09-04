package com.momentliving.dto;

import lombok.Data;

/**
 * 探店博文写操作入参 DTO（发布 / 更新共用）
 * userId、liked、comments 等由后端维护，不允许前端传入
 */
@Data
public class BlogDTO {

    /** 博客 ID（更新时必填） */
    private Long id;
    private Long shopId;
    private String title;
    private String images;
    private String content;
}
