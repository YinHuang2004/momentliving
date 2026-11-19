package com.momentliving.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momentliving.ai.common.BusinessException;
import com.momentliving.ai.common.UserIdentity;
import com.momentliving.ai.prompt.Prompts;
import com.momentliving.ai.tool.MerchantTools;
import com.momentliving.ai.tool.ShopTools;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 商家端经营助手（B 端）：经营分析 / 券文案 / 店铺介绍。
 * 数据一律来自工具的真实上游查询，LLM 只做解读与生成。
 */
@Service
public class MerchantAiService {

    private final ChatModel chatModel;
    private final MerchantTools merchantTools;
    private final ShopTools shopTools;
    private final ObjectMapper mapper = new ObjectMapper();

    public MerchantAiService(ChatModel chatModel, MerchantTools merchantTools, ShopTools shopTools) {
        this.chatModel = chatModel;
        this.merchantTools = merchantTools;
        this.shopTools = shopTools;
    }

    /** 经营分析报告：商家信息 + 今日核销统计 + 店铺评分 → LLM 解读。 */
    @SuppressWarnings("unchecked")
    public String analysis(UserIdentity identity) {
        long merchantId = identity.requireMerchant();
        ToolContext ctx = ctx(merchantId);
        Map<String, Object> me = merchantTools.me(ctx);
        long shopId = me.get("shopId") instanceof Number n ? n.longValue() : 0;
        if (shopId <= 0) {
            throw new BusinessException("当前商家未绑定店铺");
        }
        Map<String, Object> stats = merchantTools.getMerchantStats("today", ctx);
        Map<String, Object> score = merchantTools.getShopScore(ctx);
        Map<String, Object> shop = shopTools.getShopDetail(shopId);
        String merchantName = str(me.get("name"), "商家");
        String shopName = str(shop.get("name"), "-");
        String scoreText = score.containsKey("error") || score.containsKey("message")
                ? "-" : String.valueOf(score.get("avgScore") != null ? score.get("avgScore") : score.get("score"));
        String statsJson = toJson(stats);
        return chatModel.call(Prompts.MERCHANT_ANALYSIS.formatted(merchantName, shopName, scoreText, statsJson));
    }

    /** 优惠券营销文案。 */
    public String copywriting(UserIdentity identity, String voucherDesc, String sellingPoint) {
        identity.requireMerchant();
        String desc = voucherDesc == null ? "" : voucherDesc.strip();
        if (desc.isEmpty() || desc.length() > 500) {
            throw new BusinessException("券描述需在 1-500 字之间");
        }
        String point = sellingPoint == null ? "" : sellingPoint.strip();
        if (point.length() > 200) {
            throw new BusinessException("核心卖点过长（最多 200 字）");
        }
        return chatModel.call(Prompts.MERCHANT_COPYWRITING.formatted(desc, point.isEmpty() ? "-" : point));
    }

    /** 店铺介绍（100-150 字）。 */
    @SuppressWarnings("unchecked")
    public String shopIntro(UserIdentity identity) {
        long merchantId = identity.requireMerchant();
        ToolContext ctx = ctx(merchantId);
        long shopId = merchantTools.resolveShopId(ctx);
        Map<String, Object> shop = shopTools.shopDetailOrThrow(shopId);
        return chatModel.call(Prompts.SHOP_INTRO.formatted(
                shop.get("name"), shop.get("area"), shop.get("avgPrice"),
                shop.get("score"), shop.get("openHours")));
    }

    private static ToolContext ctx(long merchantId) {
        Map<String, Object> m = new HashMap<>();
        m.put("merchantId", merchantId);
        return new ToolContext(m);
    }

    private static String str(Object o, String def) {
        return o == null ? def : o.toString();
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
