package com.momentliving.controller;

import com.momentliving.config.PayProperties;
import com.momentliving.exception.BadRequestException;
import com.momentliving.result.Result;
import com.momentliving.service.PaymentService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 第三方支付异步通知入口。
 *
 * <p>约定：响应体必须是纯文本 "success" / "failure"，支付宝收到非 success 会按梯度重试。
 * 注意此 Controller 不做登录校验（MvcConfig 已放行），安全靠渠道验签兜底：
 * - 支付宝回调：RSA2 验签 + app_id/seller_id/金额一致性校验；
 * - mock 回调：受 momentliving.pay.mock-enabled 开关保护，仅本地演示环境可用。
 */
@Slf4j
@RestController
@RequestMapping("/pay")
public class PayNotifyController {

    @Resource
    private PaymentService paymentService;
    @Resource
    private PayProperties payProperties;

    /** 支付宝异步通知（form 编码参数） */
    @PostMapping(value = "/alipay/notify")
    public String alipayNotify(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k, v.length > 0 ? v[0] : ""));
        try {
            // 1. 验签 + app_id/seller_id/trade_status/金额 一致性校验
            PaymentService.AliNotifyPayload payload = paymentService.handleAlipayNotify(params);
            // 2. 状态机幂等处理（订单/流水双 CAS）
            boolean ok = paymentService.processPaid(payload.orderId(), payload.tradeNo(), params.toString());
            return ok ? "success" : "failure";
        } catch (Exception e) {
            log.error("支付宝回调处理失败", e);
            return "failure";
        }
    }

    /**
     * mock 演示回调：本地无公网时模拟第三方支付成功。
     * 与真实回调走同一个 processPaid，验签位置由开关替代（开关关闭即视为非法请求）。
     */
    @PostMapping(value = "/mock/notify")
    public String mockNotify(@RequestParam("orderId") Long orderId) {
        if (!payProperties.isMockEnabled()) {
            throw new BadRequestException("mock 支付通道未开启");
        }
        log.warn("MOCK 支付回调触发（仅演示环境使用），orderId={}", orderId);
        boolean ok = paymentService.processPaid(orderId, "mock-" + System.currentTimeMillis(), "{\"channel\":\"mock\"}");
        return ok ? "success" : "failure";
    }

    /** 全局异常处理器会返回 Result JSON；回调通道单独包装为纯文本，避免渠道解析失败 */
    @ExceptionHandler(BadRequestException.class)
    public String mockBadReq(BadRequestException e) {
        return e.getMessage();
    }
}
