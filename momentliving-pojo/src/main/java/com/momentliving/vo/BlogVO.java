package com.momentliving.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 博客视图对象：独立字段（不继承 Blog），与实体字段一一对应 + 展示字段
 * 说明：不用继承的原因是避免 images 字段与父类冲突、以及 VO 不应暴露实体结构
 */
@Data
public class BlogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ===== 与 Blog 实体一致的字段（BeanUtil.copyProperties 按名拷贝） =====
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

    // ===== 展示字段（非数据库字段） =====
    /** 作者昵称（展示用） */
    private String name;
    /** 作者头像（展示用） */
    private String authorImages;
    /** 当前登录用户是否已点赞（展示用） */
    private Boolean isLike;
    /** 当前登录用户是否已收藏（展示用，博客详情接口填充；收藏列表恒为 true） */
    private Boolean isFavorite;
}
