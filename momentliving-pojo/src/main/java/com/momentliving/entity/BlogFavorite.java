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
 * 博客收藏（blog-service 读写，表 blog_favorite）
 * (user_id, blog_id) 唯一：一个人对同一篇博客只有一条收藏记录，收藏/取消即插入/删除该行
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogFavorite implements Serializable {

    private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long blogId;
    private LocalDateTime createTime;
}
