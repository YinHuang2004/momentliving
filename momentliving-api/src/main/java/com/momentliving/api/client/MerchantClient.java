package com.momentliving.api.client;

import com.momentliving.api.config.FeignConfig;
import com.momentliving.result.Result;
import com.momentliving.vo.MerchantVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * merchant-service 内部接口客户端（🆕 ai-service 商家经营分析用）
 * /merchant/me 需商家登录态：FeignIdentityInterceptor 自动透传 X-Merchant-Id
 */
@FeignClient(name = "merchant-service", configuration = FeignConfig.class)
public interface MerchantClient {

    /** 当前登录商家信息（含 shopId，商家经营分析必用） */
    @GetMapping("/merchant/me")
    Result<MerchantVO> me();
}
