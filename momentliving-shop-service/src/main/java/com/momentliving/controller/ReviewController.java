package com.momentliving.controller;

import com.momentliving.dto.ReviewDTO;
import com.momentliving.result.Result;
import com.momentliving.service.ReviewService;
import com.momentliving.vo.ReviewVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 店铺评价接口（需登录：网关未将 /review/** 加入白名单）
 */
@RestController
@RequestMapping("/review")
public class ReviewController {

    @Resource
    private ReviewService reviewService;

    /**
     * 发表评价（JSON body，见 {@link ReviewDTO}）。
     */
    @PostMapping
    public Result<Void> addReview(@RequestBody ReviewDTO reviewDTO) {
        return reviewService.addReview(reviewDTO);
    }

    /** 店铺评价列表（分页，按时间倒序，带作者昵称/头像） */
    @GetMapping("/list/{shopId}")
    public Result<List<ReviewVO>> queryReviews(@PathVariable("shopId") Long shopId,
                                               @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return reviewService.queryReviewsByShop(shopId, current);
    }

    /** 店铺评分聚合：avg 平均分（一位小数）+ cnt 评价数（Redis 缓存 30 分钟） */
    @GetMapping("/score/{shopId}")
    public Result<Map<String, Object>> queryScore(@PathVariable("shopId") Long shopId) {
        return reviewService.queryShopScore(shopId);
    }
}
