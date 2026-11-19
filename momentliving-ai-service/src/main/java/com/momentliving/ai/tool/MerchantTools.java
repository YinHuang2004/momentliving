package com.momentliving.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家工具（仅商家态可用；身份守卫 + 先用 X-Merchant-Id 换 shopId）。
 *
 * <p>链路与 Python 版 merchant_tools.py 逐字段对齐：
 * 网关只透传 X-Merchant-Id → GET /merchant/me 换 shopId → 再查统计/评分。
 */
@Component
public class MerchantTools {

    private final ToolSupport support;

    public MerchantTools(ToolSupport support) {
        this.support = support;
    }

    @Tool(name = "get_merchant_stats",
            description = "获取当前商家的经营数据概览（今日待核销数、已核销数、营收、最近核销记录），回答\"我今天生意怎么样\"类经营问题必用（仅商家身份可用）")
    public Map<String, Object> getMerchantStats(
            @ToolParam(required = false, description = "时间范围，当前仅支持 today") String period,
            ToolContext toolContext) {
        try {
            long shopId = resolveShopId(toolContext);
            JsonNode root = support.get("voucher", "/feign/voucher-order/verify/stats",
                    Map.of("shopId", shopId), ToolSupport.identityHeaders(toolContext));
            JsonNode stats = support.extractData(root);
            if (stats.isNull() || stats.path("today").isNull() || stats.path("today").isMissingNode()) {
                return Map.of("message", "暂无经营数据");
            }
            JsonNode today = stats.path("today");
            List<Map<String, Object>> recent = new ArrayList<>();
            for (JsonNode r : stats.path("recent")) {
                if (recent.size() >= 5) {
                    break;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("voucherTitle", ToolSupport.str(r, "voucherTitle", ""));
                item.put("buyerNickName", ToolSupport.str(r, "nickName", ""));
                item.put("verifyTime", ToolSupport.str(r, "verifyTime", null));
                recent.add(item);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("period", "today");
            out.put("pendingVerify", today.path("pendingVerify").asInt(0));
            out.put("verifiedToday", today.path("verified").asInt(0));
            out.put("revenueToday", today.path("revenue").asLong(0));
            out.put("recentVerifies", recent);
            return out;
        } catch (IllegalStateException e) {
            return Map.of("error", e.getMessage());
        } catch (Exception e) {
            return Map.of("error", "经营数据查询失败：" + e.getMessage());
        }
    }

    @Tool(name = "get_shop_score",
            description = "获取当前商家店铺的评分信息（平均分、评价数），仅商家身份可用")
    public Map<String, Object> getShopScore(ToolContext toolContext) {
        try {
            long shopId = resolveShopId(toolContext);
            Map<String, String> headers = ToolSupport.identityHeaders(toolContext);
            // shop-service 的 /review/** 只认 X-User-Id 不认 X-Merchant-Id，
            // Python 版把商家 ID 临时塞进 X-User-Id 调的（评分是公开聚合数据），照搬
            if (headers.containsKey("X-Merchant-Id") && !headers.containsKey("X-User-Id")) {
                headers.put("X-User-Id", headers.get("X-Merchant-Id"));
            }
            JsonNode data = support.extractData(support.get("shop", "/review/score/" + shopId, null, headers));
            if (data.isNull() || data.isMissingNode() || data.isEmpty()) {
                return Map.of("message", "暂无评分数据");
            }
            return mapper().convertValue(data, Map.class);
        } catch (IllegalStateException e) {
            return Map.of("error", e.getMessage());
        } catch (Exception e) {
            return Map.of("error", "评分查询失败：" + e.getMessage());
        }
    }

    /** 用 X-Merchant-Id 换当前商家的 shopId（对应 Python _resolve_shop_id）。 */
    public long resolveShopId(ToolContext toolContext) {
        Map<String, String> headers = ToolSupport.identityHeaders(toolContext);
        if (!headers.containsKey("X-Merchant-Id")) {
            throw new IllegalStateException("未获取到商家身份，请重新登录商家端");
        }
        JsonNode me = support.extractData(support.get("merchant", "/merchant/me", null, headers));
        long shopId = me.path("shopId").asLong(0);
        if (shopId <= 0) {
            throw new IllegalStateException("当前商家未绑定店铺");
        }
        return shopId;
    }

    /** 商家信息（含 shopId/name），供经营分析等非工具路径复用。 */
    public Map<String, Object> me(ToolContext toolContext) {
        Map<String, String> headers = ToolSupport.identityHeaders(toolContext);
        if (!headers.containsKey("X-Merchant-Id")) {
            throw new IllegalStateException("未获取到商家身份，请重新登录商家端");
        }
        return mapper().convertValue(support.extractData(support.get("merchant", "/merchant/me", null, headers)), Map.class);
    }

    @SuppressWarnings("unchecked")
    private com.fasterxml.jackson.databind.ObjectMapper mapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper();
    }
}
