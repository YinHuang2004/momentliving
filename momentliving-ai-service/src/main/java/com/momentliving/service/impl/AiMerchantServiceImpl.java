package com.momentliving.service.impl;

import com.momentliving.api.client.MerchantClient;
import com.momentliving.api.client.ShopClient;
import com.momentliving.api.client.VoucherClient;
import com.momentliving.config.AiProperties;
import com.momentliving.context.MerchantHolder;
import com.momentliving.context.UserHolder;
import com.momentliving.entity.Shop;
import com.momentliving.exception.BadRequestException;
import com.momentliving.prompt.AiPromptConstants;
import com.momentliving.result.Result;
import com.momentliving.service.AiMerchantService;
import com.momentliving.vo.MerchantStatsVO;
import com.momentliving.vo.MerchantVO;
import com.momentliving.vo.RecentVerifyVO;
import com.momentliving.vo.ShopVO;
import com.momentliving.vo.TodayStats;
import com.momentliving.vo.UserVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 商家 AI 实现：数据全部来自实时接口（工作台统计 + 店铺评分），AI 只负责解读与撰写。
 */
@Slf4j
@Service
public class AiMerchantServiceImpl implements AiMerchantService {

    @Resource
    private ChatClient chatClient;

    @Resource
    private AiProperties aiProperties;

    @Resource
    private MerchantClient merchantClient;

    @Resource
    private VoucherClient voucherClient;

    @Resource
    private ShopClient shopClient;

    @Override
    public String analysis() {
        checkEnabled();
        MerchantVO merchant = mustGetMerchant();
        Long shopId = mustGetShopId(merchant);

        // 拉真实经营数据（核销统计 + 店铺评分）
        StringBuilder data = new StringBuilder();
        Result<MerchantStatsVO> statsResult = voucherClient.statsByShop(shopId);
        MerchantStatsVO stats = statsResult.getData();
        if (stats != null && stats.getToday() != null) {
            TodayStats today = stats.getToday();
            data.append("今日待核销：").append(nz(today.getPendingVerify()))
                    .append("；今日已核销：").append(nz(today.getVerified()))
                    .append("；今日营收：").append(nz(today.getRevenue())).append(" 元；");
        } else {
            data.append("今日暂无核销数据；");
        }
        // ⚠️ /review/** 在 shop-service 本地要求"用户态"（X-User-Id），
        // 商家态头它不认 —— 用商家 ID 临时充当查询身份（评分是公开聚合数据），调用后清理
        boolean tempSaved = false;
        if (UserHolder.getUser() == null) {
            UserHolder.saveUser(UserVO.builder().id(merchant.getId()).build());
            tempSaved = true;
        }
        Result<Map<String, Object>> scoreResult;
        try {
            scoreResult = shopClient.getShopScore(shopId);
        } finally {
            if (tempSaved) {
                UserHolder.removeUser();
            }
        }
        if (scoreResult.getData() != null) {
            data.append("店铺评分：").append(scoreResult.getData()).append("；");
        }
        if (stats != null && stats.getRecent() != null && !stats.getRecent().isEmpty()) {
            data.append("最近核销：");
            for (RecentVerifyVO item : stats.getRecent().stream().limit(5).toList()) {
                data.append(item.getVoucherTitle()).append("（").append(item.getNickName()).append("）");
            }
        }

        Shop shop = mustGetShop(shopId);
        return chatClient.prompt()
                .system(AiPromptConstants.B_END_SYSTEM)
                .user(AiPromptConstants.MERCHANT_ANALYSIS.formatted(
                        nz(merchant.getName()), shop.getName(),
                        shop.getScore() == null ? "-" : String.valueOf(shop.getScore()),
                        data))
                .call()
                .content();
    }

    @Override
    public String copywriting(String voucherDesc, String sellingPoint) {
        checkEnabled();
        if (voucherDesc == null || voucherDesc.isBlank()) {
            throw new BadRequestException("券信息不能为空");
        }
        mustGetMerchant();
        return chatClient.prompt()
                .system(AiPromptConstants.B_END_SYSTEM)
                .user(AiPromptConstants.MERCHANT_COPYWRITING.formatted(voucherDesc, sellingPoint == null ? "无" : sellingPoint))
                .call()
                .content();
    }

    @Override
    public String shopIntro() {
        checkEnabled();
        MerchantVO merchant = mustGetMerchant();
        Long shopId = mustGetShopId(merchant);
        Shop shop = mustGetShop(shopId);
        return chatClient.prompt()
                .system(AiPromptConstants.B_END_SYSTEM)
                .user(AiPromptConstants.SHOP_INTRO.formatted(
                        shop.getName(), shop.getArea() == null ? "-" : shop.getArea(),
                        shop.getAvgPrice() == null ? "-" : String.valueOf(shop.getAvgPrice()),
                        shop.getScore() == null ? "-" : String.valueOf(shop.getScore()),
                        shop.getOpenHours() == null ? "-" : shop.getOpenHours()))
                .call()
                .content();
    }

    /** 商家身份校验（网关商家态透传 X-Merchant-Id → MerchantHolder） */
    private MerchantVO mustGetMerchant() {
        MerchantVO merchant = MerchantHolder.getMerchant();
        if (merchant == null) {
            throw new BadRequestException("请先登录商家端");
        }
        // 补齐 shopId：网关只透传商家 ID，经 Feign 回查 /merchant/me（X-Merchant-Id 自动透传）
        if (merchant.getShopId() == null) {
            Result<MerchantVO> result = merchantClient.me();
            if (result.getData() != null && result.getData().getShopId() != null) {
                merchant.setShopId(result.getData().getShopId());
            }
        }
        return merchant;
    }

    private Long mustGetShopId(MerchantVO merchant) {
        if (merchant.getShopId() == null) {
            throw new BadRequestException("商家账号未绑定店铺，无法使用经营分析");
        }
        return merchant.getShopId();
    }

    private Shop mustGetShop(Long shopId) {
        Result<ShopVO> result = shopClient.getShop(shopId);
        if (result.getData() == null) {
            throw new BadRequestException("店铺不存在");
        }
        return result.getData();
    }

    private String nz(Object o) {
        return o == null ? "-" : String.valueOf(o);
    }

    private void checkEnabled() {
        if (!aiProperties.isEnabled()) {
            throw new BadRequestException("AI 功能未开启");
        }
    }
}
