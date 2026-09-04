package com.momentliving.tools;

import com.momentliving.api.client.ShopClient;
import com.momentliving.context.UserHolder;
import com.momentliving.dto.ShopQueryDTO;
import com.momentliving.result.Result;
import com.momentliving.vo.ReviewVO;
import com.momentliving.vo.ShopVO;
import com.momentliving.vo.UserVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 商铺查询工具（Function Calling）：数据一律来自 shop-service 实时接口，AI 禁止编造。
 * 工具方法只读公开数据，不依赖用户身份（公共接口）。
 */
@Slf4j
@Component
public class ShopTools {

    @Resource
    private ShopClient shopClient;

    @Tool(description = "根据关键词搜索商铺，返回商铺列表（名称、区域、地址、评分、人均）")
    public List<Map<String, Object>> searchShops(
            @ToolParam(description = "搜索关键词，如火锅店、咖啡") String keyword,
            @ToolParam(description = "排序方式：score评分最高 / sold销量最高 / other默认", required = false) String sort) {
        long start = System.currentTimeMillis();
        ShopQueryDTO query = new ShopQueryDTO();
        query.setName(keyword);
        query.setCurrent(1);
        Result<List<ShopVO>> result = shopClient.searchShops(query);
        List<ShopVO> shops = result.getData() == null ? List.of() : result.getData();
        if ("score".equals(sort)) {
            shops = shops.stream()
                    .sorted(Comparator.comparing(ShopVO::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        } else if ("sold".equals(sort)) {
            shops = shops.stream()
                    .sorted(Comparator.comparing(ShopVO::getSold, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        }
        List<Map<String, Object>> data = shops.stream().limit(10).map(this::toMap).toList();
        logTool("searchShops", Map.of("keyword", keyword), data.size(), start);
        return data;
    }

    @Tool(description = "根据商铺ID获取商铺详情（名称、地址、人均、评分、营业时间）")
    public Map<String, Object> getShopDetail(@ToolParam(description = "商铺ID") Long shopId) {
        long start = System.currentTimeMillis();
        Result<ShopVO> result = shopClient.getShop(shopId);
        Map<String, Object> data = result.getData() == null
                ? Map.of("error", "商铺不存在")
                : toMap(result.getData());
        logTool("getShopDetail", Map.of("shopId", shopId), data.size(), start);
        return data;
    }

    @Tool(description = "获取商铺的用户评价列表（内容、星级、昵称）")
    public List<Map<String, Object>> getShopReviews(@ToolParam(description = "商铺ID") Long shopId,
                                                    ToolContext toolContext) {
        long start = System.currentTimeMillis();
        // ⚠️ /review/** 在 shop-service 本地也要求登录（只有 /shop/** 公开），
        // 流式场景工具跑在无身份线程上，必须临时补 X-User-Id，否则 Feign 会被 401
        Long userId = resolveUserId(toolContext);
        if (userId == null) {
            log.warn("AI工具调用 getShopReviews 缺少用户身份，拒绝查询");
            return List.of(Map.of("error", "未获取到用户身份，请重新登录后再试"));
        }
        boolean tempSaved = false;
        if (UserHolder.getUser() == null) {
            UserHolder.saveUser(UserVO.builder().id(userId).build());
            tempSaved = true;
        }
        List<Map<String, Object>> data;
        try {
            Result<List<ReviewVO>> result = shopClient.getShopReviews(shopId, 1);
            data = (result.getData() == null ? List.<ReviewVO>of() : result.getData())
                    .stream()
                    .limit(10)
                    .map(r -> Map.<String, Object>of(
                            "rating", r.getRating() == null ? 5 : r.getRating(),
                            "content", abbreviate(r.getContent(), 100),
                            "nickName", r.getNickName() == null ? "匿名用户" : r.getNickName()))
                    .toList();
        } finally {
            if (tempSaved) {
                UserHolder.removeUser();
            }
        }
        logTool("getShopReviews", Map.of("shopId", shopId), data.size(), start);
        return data;
    }

    /** 身份还原：优先 ToolContext（流式场景），回退 UserHolder（同步场景） */
    private Long resolveUserId(ToolContext toolContext) {
        if (toolContext != null && toolContext.getContext().get("aiUserId") != null) {
            return Long.valueOf(toolContext.getContext().get("aiUserId").toString());
        }
        return UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
    }

    private Map<String, Object> toMap(ShopVO shop) {
        return Map.of(
                "shopId", shop.getId(),
                "name", shop.getName() == null ? "" : shop.getName(),
                "area", shop.getArea() == null ? "" : shop.getArea(),
                "address", shop.getAddress() == null ? "" : shop.getAddress(),
                "avgPrice", shop.getAvgPrice() == null ? 0 : shop.getAvgPrice(),
                "score", shop.getScore() == null ? 0 : shop.getScore(),
                "sold", shop.getSold() == null ? 0 : shop.getSold(),
                "openHours", shop.getOpenHours() == null ? "" : shop.getOpenHours());
    }

    private String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private void logTool(String name, Object params, int resultSize, long start) {
        log.info("AI工具调用 name={}, params={}, resultSize={}, costMs={}",
                name, params, resultSize, System.currentTimeMillis() - start);
    }
}
