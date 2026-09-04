package com.momentliving.dto;

import lombok.Data;

/**
 * AI 商铺推荐入参 DTO（POST /ai/recommend/shop）
 */
@Data
public class AiRecommendDTO {

    /** 偏好描述，如"情侣约会、人均100、西餐"（必填） */
    private String preference;

    /** 城市/区域（可选，如"青秀区"） */
    private String area;
}
