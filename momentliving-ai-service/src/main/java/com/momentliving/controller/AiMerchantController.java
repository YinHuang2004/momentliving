package com.momentliving.controller;

import com.momentliving.dto.AiMerchantCopywritingDTO;
import com.momentliving.result.Result;
import com.momentliving.service.AiMerchantService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家 AI 接口（B 端：商家 token 走网关商家态，X-Merchant-Id 透传）
 */
@RestController
@RequestMapping("/ai/merchant")
public class AiMerchantController {

    @Resource
    private AiMerchantService aiMerchantService;

    /** 经营分析助手 */
    @PostMapping("/analysis")
    public Result<String> analysis() {
        return Result.success(aiMerchantService.analysis());
    }

    /** 营销文案生成 */
    @PostMapping("/copywriting")
    public Result<String> copywriting(@RequestBody AiMerchantCopywritingDTO dto) {
        return Result.success(aiMerchantService.copywriting(dto.getVoucherDesc(), dto.getSellingPoint()));
    }

    /** 店铺介绍优化 */
    @PostMapping("/shop-intro")
    public Result<String> shopIntro() {
        return Result.success(aiMerchantService.shopIntro());
    }
}
