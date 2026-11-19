package com.momentliving.ai.controller;

import com.momentliving.ai.common.UserIdentity;
import com.momentliving.ai.dto.CopywritingRequest;
import com.momentliving.ai.service.MerchantAiService;
import com.momentliving.result.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家端 AI 接口（B 端：X-Merchant-Id）。
 */
@RestController
@RequestMapping("/ai/merchant")
public class MerchantController {

    private final MerchantAiService merchantAiService;

    public MerchantController(MerchantAiService merchantAiService) {
        this.merchantAiService = merchantAiService;
    }

    @PostMapping("/analysis")
    public Result<String> analysis(
            @RequestHeader(value = UserIdentity.HEADER_MERCHANT, required = false) String merchant) {
        return Result.success(merchantAiService.analysis(UserIdentity.of(null, merchant, null)));
    }

    @PostMapping("/copywriting")
    public Result<String> copywriting(
            @RequestHeader(value = UserIdentity.HEADER_MERCHANT, required = false) String merchant,
            @RequestBody(required = false) CopywritingRequest req) {
        String desc = req == null ? null : req.voucherDesc();
        String point = req == null ? null : req.sellingPoint();
        return Result.success(merchantAiService.copywriting(UserIdentity.of(null, merchant, null), desc, point));
    }

    @PostMapping("/shop-intro")
    public Result<String> shopIntro(
            @RequestHeader(value = UserIdentity.HEADER_MERCHANT, required = false) String merchant) {
        return Result.success(merchantAiService.shopIntro(UserIdentity.of(null, merchant, null)));
    }
}
