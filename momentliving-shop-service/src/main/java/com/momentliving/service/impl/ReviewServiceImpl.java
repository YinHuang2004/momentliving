package com.momentliving.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.momentliving.api.client.UserClient;
import com.momentliving.api.client.VoucherClient;
import com.momentliving.constant.OrderStatus;
import com.momentliving.constant.RedisConstants;
import com.momentliving.constant.SystemConstants;
import com.momentliving.context.UserHolder;
import com.momentliving.dto.ReviewDTO;
import com.momentliving.entity.Review;
import com.momentliving.entity.VoucherOrder;
import com.momentliving.exception.BadRequestException;
import com.momentliving.mapper.ReviewMapper;
import com.momentliving.result.Result;
import com.momentliving.service.ReviewService;
import com.momentliving.vo.ReviewVO;
import com.momentliving.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 店铺评价实现。
 *
 * <p>「已核销才能评」校验链：orderId 非空时 → VoucherClient 查订单（voucher-service 内部接口）
 * → 校验 userId 相同 + status == 已核销(2) → review 表查重（一单一评）。
 *
 * <p>评分聚合：Redis Hash cache:shop:score:{shopId}（avg/cnt），TTL 30 分钟；
 * 写评价后立即重算覆盖（DB AVG/COUNT 为真源），聚合失败不影响发评价主流程。
 */
@Service
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    @Resource
    private ReviewMapper reviewMapper;
    @Resource
    private VoucherClient voucherClient;
    @Resource
    private UserClient userClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result<Void> addReview(ReviewDTO reviewDTO) {
        Long shopId = reviewDTO.getShopId();
        Long orderId = reviewDTO.getOrderId();
        Integer rating = reviewDTO.getRating();
        String content = reviewDTO.getContent();
        List<String> images = reviewDTO.getImages();
        Long userId = UserHolder.getUser().getId();
        if (rating == null || rating < 1 || rating > 5) {
            throw new BadRequestException("评分必须为 1-5 星");
        }
        if (content == null || content.isBlank()) {
            throw new BadRequestException("评价内容不能为空");
        }
        if (content.length() > 500) {
            throw new BadRequestException("评价内容不能超过 500 字");
        }

        // 带 orderId：必须是本人已核销的订单，且一单一评
        if (orderId != null) {
            Result<VoucherOrder> orderResult = voucherClient.getOrder(orderId);
            VoucherOrder order = orderResult == null ? null : orderResult.getData();
            if (order == null) {
                throw new BadRequestException("关联订单不存在");
            }
            if (!order.getUserId().equals(userId)) {
                throw new BadRequestException("只能评价自己的订单");
            }
            if (order.getStatus() == null || order.getStatus() != OrderStatus.USED) {
                throw new BadRequestException("订单核销完成后才能评价");
            }
            Long exist = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                    .eq(Review::getOrderId, orderId));
            if (exist > 0) {
                throw new BadRequestException("该订单已评价过");
            }
        } else {
            // 不带订单的评价（普通点评）也限制频率：同一用户同一店一天一条
            Long mine = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                    .eq(Review::getUserId, userId)
                    .eq(Review::getShopId, shopId)
                    .gt(Review::getCreateTime, LocalDateTime.now().minusDays(1)));
            if (mine > 0) {
                throw new BadRequestException("今天已评价过该店铺");
            }
        }

        reviewMapper.insert(Review.builder()
                .userId(userId).shopId(shopId).orderId(orderId)
                .rating(rating).content(content)
                .images(images == null || images.isEmpty() ? null : String.join(",", images))
                .status(1).createTime(LocalDateTime.now())
                .build());
        log.info("评价发表成功，shopId={}, userId={}, orderId={}", shopId, userId, orderId);

        // 写后重算评分缓存（失败只记日志，不影响主流程；下次查询会自然回源 DB）
        try {
            refreshScoreCache(shopId);
        } catch (Exception e) {
            log.warn("评分缓存刷新失败（不影响评价主流程），shopId={}", shopId, e);
        }
        return Result.success();
    }

    @Override
    public Result<List<ReviewVO>> queryReviewsByShop(Long shopId, Integer current) {
        Page<Review> page = reviewMapper.selectPage(new Page<>(current, SystemConstants.MAX_PAGE_SIZE),
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getShopId, shopId)
                        .eq(Review::getStatus, 1)
                        .orderByDesc(Review::getCreateTime));
        List<Review> records = page.getRecords();
        if (records.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        // 聚合作者信息（user-service 批量接口；失败降级为仅展示评价本身）
        Map<Long, UserVO> userMap = loadAuthors(records.stream().map(Review::getUserId).distinct().toList());
        List<ReviewVO> vos = records.stream().map(r -> {
            UserVO author = userMap.get(r.getUserId());
            return ReviewVO.builder()
                    .id(r.getId()).userId(r.getUserId()).shopId(r.getShopId())
                    .orderId(r.getOrderId()).rating(r.getRating())
                    .content(r.getContent()).images(r.getImages())
                    .nickName(author == null ? null : author.getNickName())
                    .avatar(author == null ? null : author.getImages())
                    .createTime(r.getCreateTime())
                    .build();
        }).collect(Collectors.toList());
        return Result.success(vos);
    }

    @Override
    public Result<Map<String, Object>> queryShopScore(Long shopId) {
        String key = RedisConstants.SHOP_SCORE_KEY + shopId;
        Map<Object, Object> cached = stringRedisTemplate.opsForHash().entries(key);
        if (!cached.isEmpty()) {
            return Result.success(Map.of(
                    "avg", cached.getOrDefault("avg", "0"),
                    "cnt", cached.getOrDefault("cnt", "0")));
        }
        return Result.success(refreshScoreCache(shopId));
    }

    /** 重算店铺评分并写入 Redis Hash（返回计算结果） */
    private Map<String, Object> refreshScoreCache(Long shopId) {
        Double avg = reviewMapper.selectAvgRating(shopId);
        Long cnt = reviewMapper.selectReviewCount(shopId);
        String avgStr = avg == null ? "0" : String.format("%.1f", avg);
        String cntStr = cnt == null ? "0" : String.valueOf(cnt);

        Map<String, String> score = new HashMap<>(2);
        score.put("avg", avgStr);
        score.put("cnt", cntStr);
        stringRedisTemplate.opsForHash().putAll(RedisConstants.SHOP_SCORE_KEY + shopId, score);
        stringRedisTemplate.expire(RedisConstants.SHOP_SCORE_KEY + shopId,
                RedisConstants.SHOP_SCORE_TTL, TimeUnit.MINUTES);

        Map<String, Object> result = new HashMap<>(2);
        result.put("avg", avgStr);
        result.put("cnt", cntStr);
        return result;
    }

    private Map<Long, UserVO> loadAuthors(List<Long> userIds) {
        try {
            Result<List<UserVO>> resp = userClient.selectUsersByIdsOrdered(userIds);
            if (resp != null && resp.getData() != null) {
                return resp.getData().stream()
                        .collect(Collectors.toMap(UserVO::getId, Function.identity(), (a, b) -> a));
            }
        } catch (Exception e) {
            log.warn("评价作者信息聚合失败（降级为空），userIds={}", userIds, e);
        }
        return Collections.emptyMap();
    }
}
