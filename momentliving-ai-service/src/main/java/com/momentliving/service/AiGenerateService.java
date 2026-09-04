package com.momentliving.service;

import com.momentliving.dto.AiGenerateBlogDTO;
import com.momentliving.dto.AiGenerateReviewDTO;
import com.momentliving.dto.AiRecommendDTO;
import com.momentliving.vo.AiShopRecommendVO;

import java.util.List;

/**
 * AI 内容生成与推荐（C 端）
 */
public interface AiGenerateService {

    /** 商铺推荐：偏好描述 → 关键词提取 → 搜店 → 生成推荐理由 */
    List<AiShopRecommendVO> recommendShop(AiRecommendDTO dto);

    /** 探店博客草稿生成（返回"标题：xxx\n\n正文"文本） */
    String generateBlog(AiGenerateBlogDTO dto);

    /** 评价文案生成 */
    String generateReview(AiGenerateReviewDTO dto);
}
