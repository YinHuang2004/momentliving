package com.momentliving.api.client;

import com.momentliving.api.config.FeignConfig;
import com.momentliving.entity.VoucherOrder;
import com.momentliving.result.Result;
import com.momentliving.vo.MerchantStatsVO;
import com.momentliving.vo.VerifyOrderPreviewVO;
import com.momentliving.vo.VerifyRecordsVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * voucher-service 内部接口客户端
 * 使用方：shop-service（评价校验订单已核销）、admin-service（商家核销/查单/工作台统计）
 */
@FeignClient(name = "voucher-service", configuration = FeignConfig.class)
public interface VoucherClient {

    /** 查询订单（内部接口，不做本人校验；评价模块用于校验订单归属与核销状态） */
    @GetMapping("/feign/voucher-order/{id}")
    Result<VoucherOrder> getOrder(@PathVariable("id") Long orderId);

    /** 商家核销：由 admin-service 携带 X-Admin-Id 头（FeignIdentityInterceptor 自动透传）调用 */
    @PostMapping("/feign/voucher-order/verify")
    Result<Void> verifyByCode(@RequestHeader("X-Verify-Code") String verifyCode);

    /** 按核销码查订单预览（只读，商家"先核对再确认"第一步用） */
    @GetMapping("/feign/voucher-order/verify/preview/{code}")
    Result<VerifyOrderPreviewVO> previewByCode(@PathVariable("code") String code);

    /** 商家端工作台统计：今日概览 + 最近核销（按店铺维度） */
    @GetMapping("/feign/voucher-order/verify/stats")
    Result<MerchantStatsVO> statsByShop(@RequestParam("shopId") Long shopId);

    /** 商家端核销记录分页：按店铺 + 状态筛选（买家昵称由 admin-service 回填） */
    @GetMapping("/feign/voucher-order/verify/records")
    Result<VerifyRecordsVO> recordsByShop(@RequestParam("shopId") Long shopId,
                                          @RequestParam(value = "status", required = false) Integer status,
                                          @RequestParam(value = "current", defaultValue = "1") Integer current,
                                          @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize);

    /** 用户购买过（订单已支付/已核销）的店铺 id 去重集合（博客服务校验"只能发布买过的店"用） */
    @GetMapping("/feign/voucher-order/user-shop-ids")
    Result<List<Long>> userPurchasedShopIds(@RequestParam("userId") Long userId);

    // ========== 🆕 ai-service Function Calling 用 ==========

    /**
     * 查询"当前用户"的券订单列表（GET /voucher-order/my）。
     * 身份取请求头 X-User-Id（FeignIdentityInterceptor 自动透传），
     * voucher-service 的 UserContextInterceptor 会还原 UserHolder。
     */
    @GetMapping("/voucher-order/my")
    Result<List<VoucherOrder>> queryMyOrders(@RequestParam(value = "current", defaultValue = "1") Integer current,
                                             @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                             @RequestParam(value = "status", required = false) Integer status);

    /** 某店铺在售优惠券列表（GET /voucher/list/{shopId}） */
    @GetMapping("/voucher/list/{shopId}")
    Result<List<com.momentliving.vo.VoucherVO>> getShopVouchers(@PathVariable("shopId") Long shopId);
}
