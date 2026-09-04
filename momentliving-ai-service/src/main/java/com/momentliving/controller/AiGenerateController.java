package com.momentliving.controller;

import com.momentliving.dto.AiGenerateBlogDTO;
import com.momentliving.dto.AiGenerateReviewDTO;
import com.momentliving.dto.AiRecommendDTO;
import com.momentliving.result.Result;
import com.momentliving.service.AiGenerateService;
import com.momentliving.vo.AiShopRecommendVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 推荐与内容生成（C 端）
 */
@RestController
@RequestMapping("/ai")
public class AiGenerateController {

    @Resource
    private AiGenerateService aiGenerateService;

    /** 商铺推荐：偏好描述 → 真实店铺 + AI 推荐语 */
    @PostMapping("/recommend/shop")
    public Result<List<AiShopRecommendVO>> recommendShop(@RequestBody AiRecommendDTO dto) {
        return Result.success(aiGenerateService.recommendShop(dto));
    }

    /** 探店博客草稿生成（返回"标题：xxx + 正文"文本，前端可一键带入发布页） */
    @PostMapping("/generate/blog")
    public Result<String> generateBlog(@RequestBody AiGenerateBlogDTO dto) {
        return Result.success(aiGenerateService.generateBlog(dto));
    }

    /** 评价文案生成 */
    @PostMapping("/generate/review")
    public Result<String> generateReview(@RequestBody AiGenerateReviewDTO dto) {
        return Result.success(aiGenerateService.generateReview(dto));
    }
}
