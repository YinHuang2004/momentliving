package com.momentliving.tools;

import com.momentliving.api.client.ShopClient;
import com.momentliving.api.client.VoucherClient;
import com.momentliving.context.UserHolder;
import com.momentliving.result.Result;
import com.momentliving.vo.MerchantStatsVO;
import com.momentliving.vo.RecentVerifyVO;
import com.momentliving.vo.TodayStats;
import com.momentliving.vo.UserVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 商家经营数据工具（Function Calling，仅商家态）。
 *
 * <p>⚠️ 身份安全：merchantId / shopId 从 ToolContext 还原（请求开始时由网关透传的
 * X-Merchant-Id 解析写入），AI 无法查其他商家的数据。
 */
@Slf4j
@Component
public class MerchantTools {

    @Resource
    private VoucherClient voucherClient;

    @Resource
    private ShopClient shopClient;

    @Tool(description = "获取当前商家的经营数据概览（今日待核销数、已核销数、营收、最近核销记录），回答经营类问题必用")
    public Map<String, Object> getMerchantStats(
            @ToolParam(description = "时间范围：today今日 / week本周 / month本月（当前数据源仅支持today，其余返回提示）") String period,
            ToolContext toolContext) {
        long start = System.currentTimeMillis();
        Long shopId = resolveShopId(toolContext);
        if (shopId == null) {
            log.warn("AI工具调用 getMerchantStats 缺少商家身份，拒绝查询");
            return Map.of("error", "未获取到商家身份，请重新登录商家端");
        }
        Result<MerchantStatsVO> result = voucherClient.statsByShop(shopId);
        MerchantStatsVO stats = result.getData();
        if (stats == null || stats.getToday() == null) {
            return Map.of("message", "暂无经营数据");
        }
        TodayStats today = stats.getToday();
        Map<String, Object> data = Map.of(
                "period", "today",
                "pendingVerify", today.getPendingVerify() == null ? 0 : today.getPendingVerify(),
                "verifiedToday", today.getVerified() == null ? 0 : today.getVerified(),
                "revenueToday", today.getRevenue() == null ? 0 : today.getRevenue(),
                "recentVerifies", (stats.getRecent() == null ? List.<RecentVerifyVO>of() : stats.getRecent())
                        .stream()
                        .limit(5)
                        .map(r -> Map.of(
                                "voucherTitle", r.getVoucherTitle() == null ? "" : r.getVoucherTitle(),
                                "buyerNickName", r.getNickName() == null ? "" : r.getNickName(),
                                "verifyTime", String.valueOf(r.getVerifyTime())))
                        .toList());
        log.info("AI工具调用 merchantId={} shopId={} name=getMerchantStats costMs={}",
                resolveMerchantId(toolContext), shopId, System.currentTimeMillis() - start);
        return data;
    }

    @Tool(description = "获取当前商家店铺的评分信息（平均分、评价数）")
    public Map<String, Object> getShopScore(ToolContext toolContext) {
        long start = System.currentTimeMillis();
        Long shopId = resolveShopId(toolContext);
        if (shopId == null) {
            return Map.of("error", "未获取到商家身份，请重新登录商家端");
        }
        // ⚠️ /review/** 在 shop-service 本地要求"用户态"（X-User-Id），商家态头它不认，
        // 用商家 ID 临时充当查询身份（评分是公开聚合数据，与身份无关），调用后清理
        Long merchantId = resolveMerchantId(toolContext);
        boolean tempSaved = false;
        if (UserHolder.getUser() == null) {
            UserHolder.saveUser(UserVO.builder().id(merchantId != null ? merchantId : shopId).build());
            tempSaved = true;
        }
        Map<String, Object> data;
        try {
            Result<Map<String, Object>> result = shopClient.getShopScore(shopId);
            data = result.getData() == null ? Map.of("message", "暂无评分数据") : result.getData();
        } finally {
            if (tempSaved) {
                UserHolder.removeUser();
            }
        }
        log.info("AI工具调用 shopId={} name=getShopScore costMs={}", shopId, System.currentTimeMillis() - start);
        return data;
    }

    private Long resolveShopId(ToolContext toolContext) {
        if (toolContext != null && toolContext.getContext().get("aiMerchantShopId") != null) {
            return Long.valueOf(toolContext.getContext().get("aiMerchantShopId").toString());
        }
        return null;
    }

    private Long resolveMerchantId(ToolContext toolContext) {
        if (toolContext != null && toolContext.getContext().get("aiMerchantId") != null) {
            return Long.valueOf(toolContext.getContext().get("aiMerchantId").toString());
        }
        return null;
    }
}
