package com.momentliving.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.momentliving.api.client.ShopClient;
import com.momentliving.config.AiProperties;
import com.momentliving.dto.AiGenerateBlogDTO;
import com.momentliving.dto.AiGenerateReviewDTO;
import com.momentliving.dto.AiRecommendDTO;
import com.momentliving.dto.ShopQueryDTO;
import com.momentliving.entity.Shop;
import com.momentliving.exception.BadRequestException;
import com.momentliving.prompt.AiPromptConstants;
import com.momentliving.result.Result;
import com.momentliving.service.AiGenerateService;
import com.momentliving.vo.AiShopRecommendVO;
import com.momentliving.vo.ShopVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 内容生成与推荐实现。
 * 推荐链路：偏好描述 → AI 提取搜索关键词 → shop-service 搜真实店铺 → AI 生成推荐理由（JSON 解析，失败兜底）。
 */
@Slf4j
@Service
public class AiGenerateServiceImpl implements AiGenerateService {

    @Resource
    private ChatClient chatClient;

    @Resource
    private AiProperties aiProperties;

    @Resource
    private ShopClient shopClient;

    @Override
    public List<AiShopRecommendVO> recommendShop(AiRecommendDTO dto) {
        checkEnabled();
        if (dto == null || dto.getPreference() == null || dto.getPreference().isBlank()) {
            throw new BadRequestException("偏好描述不能为空");
        }

        // 1. 提取搜索关键词
        String keyword = chatClient.prompt()
                .user(AiPromptConstants.RECOMMEND_EXTRACT.formatted(dto.getPreference()))
                .call()
                .content();
        keyword = keyword == null ? "" : keyword.trim().replaceAll("[\"'。]", "");
        if (keyword.length() > 20) {
            keyword = keyword.substring(0, 20);
        }
        if (keyword.isEmpty()) {
            throw new BadRequestException("没能从偏好描述中提取到店铺关键词，请描述得更具体些");
        }

        // 2. 搜真实店铺
        ShopQueryDTO query = new ShopQueryDTO();
        query.setName(keyword);
        if (dto.getArea() != null && !dto.getArea().isBlank()) {
            // ShopQueryDTO 无 area 字段，关键词追加区域提高命中率
            query.setName(keyword + " " + dto.getArea().trim());
        }
        query.setCurrent(1);
        Result<List<ShopVO>> result = shopClient.searchShops(query);
        List<ShopVO> shops = result.getData() == null ? List.of() : result.getData();
        if (shops.isEmpty()) {
            throw new BadRequestException("没有找到与「" + keyword + "」相关的店铺，换个关键词试试");
        }

        // 3. AI 生成推荐理由（JSON 输出解析，失败兜底）
        String candidates = JSONUtil.toJsonStr(shops.stream().limit(10).map(this::brief).toList());
        List<AiShopRecommendVO> recommendations = new ArrayList<>();
        try {
            String reply = chatClient.prompt()
                    .user(AiPromptConstants.RECOMMEND_REASON.formatted(dto.getPreference(), candidates))
                    .call()
                    .content();
            JSONArray array = JSONUtil.parseArray(stripCodeFence(reply));
            for (Object item : array) {
                JSONObject json = (JSONObject) item;
                Long shopId = json.getLong("shopId", null);
                shops.stream()
                        .filter(s -> s.getId().equals(shopId))
                        .findFirst()
                        .ifPresent(s -> recommendations.add(toVO(s, json.getStr("reason", ""))));
                if (recommendations.size() >= 5) {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("推荐理由解析失败，使用兜底推荐语 keyword={}", keyword, e);
        }
        if (recommendations.isEmpty()) {
            // 兜底：取前 5 家 + 默认推荐语
            shops.stream().limit(5).forEach(s -> recommendations.add(toVO(s, "契合你的偏好「" + dto.getPreference() + "」，值得一试")));
        }
        return recommendations;
    }

    @Override
    public String generateBlog(AiGenerateBlogDTO dto) {
        checkEnabled();
        Shop shop = mustGetShop(dto == null ? null : dto.getShopId());
        String style = switch (dto.getStyle() == null ? "simple" : dto.getStyle()) {
            case "humor" -> "幽默风趣，带点段子感";
            case "literary" -> "文艺清新，有画面感";
            default -> "简洁自然，信息量足";
        };
        String keywordLine = dto.getKeywords() == null || dto.getKeywords().isBlank()
                ? "" : "用户想突出的关键词/内容：" + dto.getKeywords();
        return chatClient.prompt()
                .user(AiPromptConstants.GENERATE_BLOG.formatted(
                        style, keywordLine, shop.getName(), nz(shop.getArea()), nz(shop.getAddress()),
                        shop.getAvgPrice() == null ? "-" : String.valueOf(shop.getAvgPrice()),
                        shop.getScore() == null ? "-" : String.valueOf(shop.getScore()),
                        nz(shop.getOpenHours())))
                .call()
                .content();
    }

    @Override
    public String generateReview(AiGenerateReviewDTO dto) {
        checkEnabled();
        if (dto == null || dto.getRating() == null || dto.getRating() < 1 || dto.getRating() > 5) {
            throw new BadRequestException("评分必须为 1-5 星");
        }
        if (dto.getImpression() == null || dto.getImpression().isBlank()) {
            throw new BadRequestException("简短感受不能为空");
        }
        Shop shop = mustGetShop(dto.getShopId());
        return chatClient.prompt()
                .user(AiPromptConstants.GENERATE_REVIEW.formatted(
                        shop.getName(), dto.getRating(), dto.getImpression()))
                .call()
                .content();
    }

    private Shop mustGetShop(Long shopId) {
        if (shopId == null) {
            throw new BadRequestException("店铺 ID 不能为空");
        }
        Result<ShopVO> result = shopClient.getShop(shopId);
        if (result.getData() == null) {
            throw new BadRequestException("店铺不存在");
        }
        return result.getData();
    }

    private AiShopRecommendVO toVO(ShopVO shop, String reason) {
        return AiShopRecommendVO.builder()
                .shopId(shop.getId())
                .name(shop.getName())
                .area(shop.getArea())
                .address(shop.getAddress())
                .avgPrice(shop.getAvgPrice())
                .score(shop.getScore())
                .reason(reason == null || reason.isBlank() ? "契合你的偏好，值得一试" : reason)
                .build();
    }

    private Object brief(ShopVO shop) {
        JSONObject json = new JSONObject();
        json.set("shopId", shop.getId());
        json.set("name", nz(shop.getName()));
        json.set("area", nz(shop.getArea()));
        json.set("avgPrice", shop.getAvgPrice());
        json.set("score", shop.getScore());
        return json;
    }

    /** 去掉模型输出可能带的 ```json 围栏 */
    private String stripCodeFence(String reply) {
        if (reply == null) {
            return "[]";
        }
        return reply.replaceAll("```(json)?", "").trim();
    }

    private String nz(String s) {
        return s == null ? "-" : s;
    }

    private void checkEnabled() {
        if (!aiProperties.isEnabled()) {
            throw new BadRequestException("AI 功能未开启");
        }
    }
}
