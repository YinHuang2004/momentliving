package com.momentliving.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.momentliving.ai.common.BusinessException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C 端店铺工具（上游 shop-service）。
 */
@Component
public class ShopTools {

    private final ToolSupport support;

    public ShopTools(ToolSupport support) {
        this.support = support;
    }

    @Tool(name = "search_shops",
            description = "根据关键词搜索商铺，返回商铺列表（名称、区域、地址、评分、人均、销量），回答\"找店/推荐店铺\"类问题必用")
    public List<Map<String, Object>> searchShops(
            @ToolParam(description = "搜索关键词，如菜系、品类或店铺名") String keyword,
            @ToolParam(required = false, description = "排序方式：score=评分优先，sold=销量优先") String sort) {
        try {
            JsonNode items = support.extractList(support.get("shop", "/shop/of/name",
                    Map.of("name", keyword, "current", 1, "sort", sort == null || sort.isBlank() ? "other" : sort),
                    null));
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode item : items) {
                if (out.size() >= 10) {
                    break;
                }
                Map<String, Object> shop = new LinkedHashMap<>();
                shop.put("shopId", ToolSupport.num(item, "id"));
                shop.put("name", ToolSupport.str(item, "name", ""));
                shop.put("area", ToolSupport.str(item, "area", ""));
                shop.put("address", ToolSupport.str(item, "address", ""));
                shop.put("avgPrice", ToolSupport.num(item, "avgPrice"));
                shop.put("score", ToolSupport.num(item, "score"));
                shop.put("sold", ToolSupport.num(item, "sold"));
                out.add(shop);
            }
            if ("score".equals(sort)) {
                out.sort((a, b) -> Double.compare(asDouble(b.get("score")), asDouble(a.get("score"))));
            } else if ("sold".equals(sort)) {
                out.sort((a, b) -> Double.compare(asDouble(b.get("sold")), asDouble(a.get("sold"))));
            }
            return out;
        } catch (Exception e) {
            return ToolSupport.errorList("搜索服务异常：" + e.getMessage());
        }
    }

    @Tool(name = "get_shop_detail",
            description = "根据商铺ID获取商铺详情（名称、区域、地址、人均、评分、销量、营业时间）")
    public Map<String, Object> getShopDetail(
            @ToolParam(description = "商铺ID") long shopId) {
        try {
            JsonNode item = support.extractData(support.get("shop", "/shop/" + shopId, null, null));
            if (item.isEmpty()) {
                return Map.of("error", "商铺不存在");
            }
            Map<String, Object> shop = new LinkedHashMap<>();
            shop.put("shopId", ToolSupport.num(item, "id"));
            shop.put("name", ToolSupport.str(item, "name", ""));
            shop.put("area", ToolSupport.str(item, "area", ""));
            shop.put("address", ToolSupport.str(item, "address", ""));
            shop.put("avgPrice", ToolSupport.num(item, "avgPrice"));
            shop.put("score", ToolSupport.num(item, "score"));
            shop.put("sold", ToolSupport.num(item, "sold"));
            shop.put("openHours", ToolSupport.str(item, "openHours", ""));
            return shop;
        } catch (Exception e) {
            return Map.of("error", "商铺查询失败：" + e.getMessage());
        }
    }

    @Tool(name = "get_shop_reviews",
            description = "获取商铺的用户评价列表（星级、内容、昵称），回答\"这家店评价怎么样\"类问题可用")
    public List<Map<String, Object>> getShopReviews(
            @ToolParam(description = "商铺ID") long shopId,
            ToolContext toolContext) {
        Map<String, String> headers = ToolSupport.identityHeaders(toolContext);
        if (headers.isEmpty()) {
            return ToolSupport.errorList("未获取到用户身份，请重新登录后再试");
        }
        try {
            JsonNode items = support.extractList(support.get("shop", "/review/list/" + shopId,
                    Map.of("current", 1), headers));
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode item : items) {
                if (out.size() >= 10) {
                    break;
                }
                Map<String, Object> review = new LinkedHashMap<>();
                Object rating = ToolSupport.num(item, "rating");
                review.put("rating", rating == null ? 5 : rating);
                review.put("content", ToolSupport.truncate(ToolSupport.str(item, "content", ""), 100));
                review.put("nickName", ToolSupport.str(item, "nickName", "匿名用户"));
                out.add(review);
            }
            return out;
        } catch (Exception e) {
            return ToolSupport.errorList("评价查询失败：" + e.getMessage());
        }
    }

    /** 供 GenerateService 复用（非工具路径直调）。 */
    public Map<String, Object> shopDetailOrThrow(long shopId) {
        Map<String, Object> shop = getShopDetail(shopId);
        if (shop.containsKey("error")) {
            throw new BusinessException("店铺查询失败：" + shop.get("error"));
        }
        return shop;
    }

    private static double asDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }
}
