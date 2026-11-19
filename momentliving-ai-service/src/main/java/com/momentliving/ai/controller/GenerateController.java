package com.momentliving.ai.controller;

import com.momentliving.ai.common.UserIdentity;
import com.momentliving.ai.dto.GenerateBlogRequest;
import com.momentliving.ai.dto.GenerateReviewRequest;
import com.momentliving.ai.dto.RecommendRequest;
import com.momentliving.ai.dto.RecommendShopVO;
import com.momentliving.ai.service.GenerateService;
import com.momentliving.result.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内容生成与推荐接口（C 端）。
 */
@RestController
@RequestMapping("/ai")
public class GenerateController {

    private final GenerateService generateService;

    public GenerateController(GenerateService generateService) {
        this.generateService = generateService;
    }

    @PostMapping("/recommend/shop")
    public Result<List<RecommendShopVO>> recommendShop(
            @RequestHeader(value = UserIdentity.HEADER_USER, required = false) String user,
            @RequestHeader(value = UserIdentity.HEADER_MERCHANT, required = false) String merchant,
            @RequestBody(required = false) RecommendRequest req) {
        UserIdentity identity = UserIdentity.of(user, merchant, null).requireChatUser();
        return Result.success(generateService.recommendShop(identity, req));
    }

    @PostMapping("/generate/blog")
    public Result<String> generateBlog(
            @RequestHeader(value = UserIdentity.HEADER_USER, required = false) String user,
            @RequestHeader(value = UserIdentity.HEADER_MERCHANT, required = false) String merchant,
            @RequestBody(required = false) GenerateBlogRequest req) {
        UserIdentity identity = UserIdentity.of(user, merchant, null).requireChatUser();
        return Result.success(generateService.generateBlog(identity, req));
    }

    @PostMapping("/generate/review")
    public Result<String> generateReview(
            @RequestHeader(value = UserIdentity.HEADER_USER, required = false) String user,
            @RequestHeader(value = UserIdentity.HEADER_MERCHANT, required = false) String merchant,
            @RequestBody(required = false) GenerateReviewRequest req) {
        UserIdentity identity = UserIdentity.of(user, merchant, null).requireChatUser();
        return Result.success(generateService.generateReview(identity, req));
    }
}
