package com.momentliving.api.client;

import com.momentliving.api.config.FeignConfig;
import com.momentliving.dto.ShopDTO;
import com.momentliving.dto.ShopQueryDTO;
import com.momentliving.result.Result;
import com.momentliving.vo.ReviewVO;
import com.momentliving.vo.ShopVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * shop-service 客户端（复用公开查询接口 /shop/{id}，网关对该前缀的 GET 已放行）
 * 使用方：blog-service（发布博客时校验/回显用户购买过的店铺）、admin-service（商家入驻审核建店）
 */
@FeignClient(name = "shop-service", configuration = FeignConfig.class)
public interface ShopClient {

    /** 店铺详情（不存在返回 data=null） */
    @GetMapping("/shop/{id}")
    Result<ShopVO> getShop(@PathVariable("id") Long shopId);

    /**
     * ★ 创建店铺（内部接口 /shop/feign/create，返回自增 shopId）。
     *   仅限商家入驻审核链路调用：调用方方法上须有 @GlobalTransactional，
     *   Seata 会把 XID 写进 Feign 请求头，下游据此注册分支事务，失败全局回滚。
     */
    @PostMapping("/shop/feign/create")
    Result<Long> create(@RequestBody ShopDTO shop);

    // ========== 🆕 ai-service Function Calling 用（只读复用现有公开接口） ==========

    /** 按名称关键词搜索店铺（GET /shop/of/name，GET query 绑定 ShopQueryDTO） */
    @GetMapping("/shop/of/name")
    Result<List<ShopVO>> searchShops(@SpringQueryMap ShopQueryDTO query);

    /** 店铺评价列表（GET /review/list/{shopId}，落在 shop-service） */
    @GetMapping("/review/list/{shopId}")
    Result<List<ReviewVO>> getShopReviews(@PathVariable("shopId") Long shopId,
                                          @RequestParam(value = "current", defaultValue = "1") Integer current);

    /** 店铺评分聚合（GET /review/score/{shopId}，返回 {avg, cnt}） */
    @GetMapping("/review/score/{shopId}")
    Result<Map<String, Object>> getShopScore(@PathVariable("shopId") Long shopId);
}
