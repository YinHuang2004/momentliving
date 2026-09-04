package com.momentliving.controller;

import com.momentliving.entity.VoucherOrder;
import com.momentliving.exception.BadRequestException;
import com.momentliving.result.Result;
import com.momentliving.service.VerifyService;
import com.momentliving.mapper.VoucherOrderMapper;
import com.momentliving.vo.MerchantStatsVO;
import com.momentliving.vo.VerifyOrderPreviewVO;
import com.momentliving.vo.VerifyRecordsVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 服务内部接口（仅限内网 Feign 调用，网关不路由 /feign/**）：
 * - shop-service：评价时校验订单归属与核销状态
 * - admin-service：商家扫码核销（携带 X-Admin-Id 透传操作人）
 */
@RestController
@RequestMapping("/feign/voucher-order")
public class InternalVoucherController {

    @Resource
    private VoucherOrderMapper voucherOrderMapper;
    @Resource
    private VerifyService verifyService;

    /** 内部查询订单原文（调用方自行校验业务规则） */
    @GetMapping("/{id}")
    public Result<VoucherOrder> getOrder(@PathVariable("id") Long orderId) {
        VoucherOrder order = voucherOrderMapper.selectById(orderId);
        return order == null ? Result.error("订单不存在") : Result.success(order);
    }

    /**
     * 商家核销。
     *
     * @param verifyCode 核销码，经 X-Verify-Code 头传递
     * @param merchantId 操作商家账号 ID（merchant.id），由 FeignIdentityInterceptor 以 X-Merchant-Id 透传，
     *                   核销记录 voucher_verify.verify_by 记该值
     * @param shopId     🆕 核销商家绑定的店铺 ID（X-Merchant-Shop-Id）：
     *                   用于"券适用范围"校验（单店券仅限本店、多店券须在名单内、全场通用不限）
     *                   以及核销归属回填（全场/多店券核销后 voucher_verify.shop_id 记实际核销店铺）
     */
    @PostMapping("/verify")
    public Result<Void> verifyByCode(@RequestHeader("X-Verify-Code") String verifyCode,
                                     @RequestHeader("X-Merchant-Id") Long merchantId,
                                     @RequestHeader(value = "X-Merchant-Shop-Id", required = false) Long shopId) {
        if (merchantId == null) {
            throw new BadRequestException("缺少商家身份信息");
        }
        verifyService.verifyByCode(verifyCode, merchantId, shopId);
        return Result.success();
    }

    /**
     * 按核销码查订单预览（admin-service 代理，商家"先核对再确认"第一步，只读）。
     *
     * @param code 16 位核销码
     */
    @GetMapping("/verify/preview/{code}")
    public Result<VerifyOrderPreviewVO> previewByCode(@PathVariable("code") String code) {
        return Result.success(verifyService.previewByCode(code));
    }

    /**
     * 商家端工作台统计（admin-service 代理，按店铺维度）。
     *
     * @param shopId 核销店铺ID
     */
    @GetMapping("/verify/stats")
    public Result<MerchantStatsVO> stats(@RequestParam("shopId") Long shopId) {
        return Result.success(verifyService.stats(shopId));
    }

    /**
     * 商家端核销记录分页（admin-service 代理，按店铺 + 状态筛选）。
     */
    @GetMapping("/verify/records")
    public Result<VerifyRecordsVO> records(@RequestParam("shopId") Long shopId,
                                           @RequestParam(value = "status", required = false) Integer status,
                                           @RequestParam(value = "current", defaultValue = "1") Integer current,
                                           @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return Result.success(verifyService.pageRecords(shopId, status, current, pageSize));
    }

    /**
     * 用户购买过（订单已支付/已核销）的店铺 id 去重集合（blog-service 发布博客校验用）。
     */
    @GetMapping("/user-shop-ids")
    public Result<List<Long>> userPurchasedShopIds(@RequestParam("userId") Long userId) {
        return Result.success(voucherOrderMapper.selectPurchasedShopIds(userId));
    }
}
