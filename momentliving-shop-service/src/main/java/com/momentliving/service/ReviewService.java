package com.momentliving.service;

import com.momentliving.dto.ReviewDTO;
import com.momentliving.result.Result;
import com.momentliving.vo.ReviewVO;

import java.util.List;
import java.util.Map;

/**
 * 店铺评价服务：
 * - 发评价（带 orderId 时校验「本人 + 已核销」，一单一评）
 * - 评价列表（分页 + 作者信息聚合）
 * - 店铺评分聚合（Redis Hash 缓存，写评价后重算）
 */
public interface ReviewService {

    /**
     * 发表评价（入参见 {@link ReviewDTO}）。
     */
    Result<Void> addReview(ReviewDTO reviewDTO);

    /** 店铺评价列表（分页，按时间倒序，聚合作者昵称/头像） */
    Result<List<ReviewVO>> queryReviewsByShop(Long shopId, Integer current);

    /** 店铺评分聚合：avg（一位小数）+ cnt（评价总数），Redis 缓存 30 分钟 */
    Result<Map<String, Object>> queryShopScore(Long shopId);
}
