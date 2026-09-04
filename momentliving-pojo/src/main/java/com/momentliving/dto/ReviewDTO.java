package com.momentliving.dto;

import lombok.Data;

import java.util.List;

/**
 * 发表评价入参 DTO（替代 5 个散参）
 */
@Data
public class ReviewDTO {

    /** 店铺 ID（必填） */
    private Long shopId;

    /** 关联订单 ID（可选：提供则要求本人订单已核销，一单一评） */
    private Long orderId;

    /** 星级 1-5（必填） */
    private Integer rating;

    /** 评价内容，≤500 字（必填） */
    private String content;

    /** 图片 URL 列表（先调 /file/upload 上传拿 URL，可选） */
    private List<String> images;
}
