package com.momentliving.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 店铺评价 VO：评价原文 + 作者信息（user-service Feign 聚合）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private Long shopId;

    /** 关联订单ID（已核销订单的评价，可信度高） */
    private Long orderId;

    /** 星级 1-5 */
    private Integer rating;

    private String content;

    /** 图片 URL（逗号分隔，先经 file-service 上传） */
    private String images;

    /** 作者昵称（user-service 聚合，失败时为 null，前端容错展示） */
    private String nickName;

    /** 作者头像（同上） */
    private String avatar;

    private LocalDateTime createTime;
}
