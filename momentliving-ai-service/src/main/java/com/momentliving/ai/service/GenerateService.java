package com.momentliving.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.momentliving.ai.common.BusinessException;
import com.momentliving.ai.common.UserIdentity;
import com.momentliving.ai.dto.GenerateBlogRequest;
import com.momentliving.ai.dto.GenerateReviewRequest;
import com.momentliving.ai.dto.RecommendRequest;
import com.momentliving.ai.dto.RecommendShopVO;
import com.momentliving.ai.prompt.Prompts;
import com.momentliving.ai.tool.ShopTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 内容生成与推荐（C 端，无记忆无工具循环的独立 LLM 调用）。
 */
@Service
public class GenerateService {

    private static final Logger log = LoggerFactory.getLogger(GenerateService.class);

    private final ChatModel chatModel;
    private final ShopTools shopTools;
    private final ObjectMapper mapper = new ObjectMapper();

    public GenerateService(ChatModel chatModel, ShopTools shopTools) {
        this.chatModel = chatModel;
        this.shopTools = shopTools;
    }

    private static final Map<String, String> STYLE_TEXT = Map.of(
            "simple", "简洁直白，突出实用信息",
            "humor", "幽默风趣、有梗，网感强",
            "literary", "文艺细腻，有画面感");

    /** 店铺推荐：LLM 提取搜索关键词 → 工具查真实店铺 → LLM 生成推荐理由（解析失败退兜底文案）。 */
    @SuppressWarnings("unchecked")
    public List<RecommendShopVO> recommendShop(UserIdentity identity, RecommendRequest req) {
        identity.requireChatUser();
        String preference = req.preference() == null ? "" : req.preference().strip();
        if (preference.isEmpty() || preference.length() > 500) {
            throw new BusinessException("偏好描述需在 1-500 字之间");
        }
        // 第一步：LLM 从偏好描述提取搜索关键词
        String keyword = chatModel.call(Prompts.RECOMMEND_EXTRACT.formatted(preference));
        keyword = keyword == null || keyword.isBlank() ? preference : keyword.strip();
        // 第二步：真实店铺数据（复用工具的上游调用与字段映射）
        List<Map<String, Object>> shops = shopTools.searchShops(keyword, "score");
        shops.removeIf(s -> s.containsKey("error"));
        if (shops.isEmpty()) {
            return List.of();
        }
        // 第三步：LLM 为候选店铺生成个性化推荐理由
        String candidates;
        try {
            candidates = mapper.writeValueAsString(shops);
        } catch (Exception e) {
            candidates = "[]";
        }
        List<RecommendShopVO> result = new ArrayList<>();
        try {
            String answer = chatModel.call(Prompts.RECOMMEND_REASON.formatted(preference, candidates));
            List<Map<String, Object>> reasons = mapper.readValue(stripJsonFence(answer),
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            for (Map<String, Object> r : reasons) {
                if (result.size() >= 5) {
                    break;
                }
                long shopId = ((Number) r.get("shopId")).longValue();
                String reason = r.get("reason") == null ? Prompts.REASON_FALLBACK : r.get("reason").toString();
                result.add(toVO(findShop(shops, shopId), reason));
            }
        } catch (Exception e) {
            log.warn("推荐理由解析失败（退化为兜底文案）", e);
        }
        // 解析失败/缺项：用真实店铺数据 + 兜底理由补齐
        if (result.isEmpty()) {
            for (Map<String, Object> shop : shops) {
                if (result.size() >= 5) {
                    break;
                }
                result.add(toVO(shop, Prompts.REASON_FALLBACK));
            }
        }
        return result;
    }

    /** 探店笔记：取真实店铺详情 + 文风映射拼 prompt，单次 LLM 调用。 */
    public String generateBlog(UserIdentity identity, GenerateBlogRequest req) {
        identity.requireChatUser();
        if (req.shopId() == null) {
            throw new BusinessException("shopId 不能为空");
        }
        String style = req.style() == null || req.style().isBlank() ? "simple" : req.style();
        if (!STYLE_TEXT.containsKey(style)) {
            style = "simple";
        }
        String keywords = req.keywords() == null ? "" : req.keywords().strip();
        if (keywords.length() > 200) {
            throw new BusinessException("关键词过长（最多 200 字）");
        }
        String keywordLine = keywords.isEmpty() ? "" : "必须包含的关键词：" + keywords;
        Map<String, Object> shop = shopTools.shopDetailOrThrow(req.shopId());
        String prompt = Prompts.GENERATE_BLOG.formatted(
                STYLE_TEXT.get(style), keywordLine,
                shop.get("name"), shop.get("area"), shop.get("address"),
                shop.get("avgPrice"), shop.get("score"), shop.get("openHours"));
        return chatModel.call(prompt);
    }

    /** 评价扩写：校验评分与感受长度，取真实店铺信息拼 prompt，单次 LLM 调用。 */
    public String generateReview(UserIdentity identity, GenerateReviewRequest req) {
        identity.requireChatUser();
        if (req.shopId() == null) {
            throw new BusinessException("shopId 不能为空");
        }
        Integer rating = req.rating();
        if (rating == null || rating < 1 || rating > 5) {
            throw new BusinessException("评分需在 1-5 星之间");
        }
        String impression = req.impression() == null ? "" : req.impression().strip();
        if (impression.isEmpty() || impression.length() > 500) {
            throw new BusinessException("感受描述需在 1-500 字之间");
        }
        Map<String, Object> shop = shopTools.shopDetailOrThrow(req.shopId());
        return chatModel.call(Prompts.GENERATE_REVIEW.formatted(
                shop.get("name"), rating, impression));
    }

    private static Map<String, Object> findShop(List<Map<String, Object>> shops, long shopId) {
        return shops.stream()
                .filter(s -> s.get("shopId") instanceof Number n && n.longValue() == shopId)
                .findFirst()
                .orElse(null);
    }

    private static RecommendShopVO toVO(Map<String, Object> shop, String reason) {
        if (shop == null) {
            return new RecommendShopVO(0, "", "", "", "", 0.0, reason);
        }
        return new RecommendShopVO(
                ((Number) shop.getOrDefault("shopId", 0)).longValue(),
                (String) shop.getOrDefault("name", ""),
                (String) shop.getOrDefault("area", ""),
                (String) shop.getOrDefault("address", ""),
                String.valueOf(shop.getOrDefault("avgPrice", "")),
                shop.get("score") instanceof Number n ? n.doubleValue() : 0.0,
                reason);
    }

    /** 剥掉 LLM 可能包裹的 ```json 围栏。 */
    private static String stripJsonFence(String answer) {
        if (answer == null) {
            return "[]";
        }
        String s = answer.strip();
        if (s.startsWith("```")) {
            int first = s.indexOf('\n');
            int last = s.lastIndexOf("```");
            if (first > 0 && last > first) {
                s = s.substring(first + 1, last).strip();
            }
        }
        return s;
    }

    /** 供商家服务复用的 ToolContext 构造。 */
    static ToolContext toolContext(Map<String, Object> ctx) {
        return new ToolContext(ctx);
    }
}
