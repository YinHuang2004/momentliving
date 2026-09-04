package com.momentliving.controller;

import com.momentliving.result.Result;
import com.momentliving.service.PaymentService;
import com.momentliving.vo.PayOrderVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 订单支付接口（用户端）
 */
@RestController
@RequestMapping("/pay")
public class PayController {

    @Resource
    private PaymentService paymentService;

    /**
     * 发起支付
     *
     * @param payType 支付方式：1微信（未开通） 2支付宝 3mock 演示（需开启 momentliving.pay.mock-enabled）
     * @return payContent：支付宝渠道为自动提交 form HTML，mock 渠道为演示页 HTML
     */
    @PostMapping("/create/{orderId}")
    public Result<PayOrderVO> createPayment(@PathVariable("orderId") Long orderId,
                                            @RequestParam("payType") Integer payType) {
        return paymentService.createPayment(orderId, payType);
    }

    /**
     * 支付状态查询（前端轮询：orderStatus 0待支付 1已支付 2已核销 3已退款 4已关闭；
     * payStatus 为支付流水状态，可能为 null 表示尚未发起支付）
     */
    @GetMapping("/status/{orderId}")
    public Result<Map<String, Object>> queryPayStatus(@PathVariable("orderId") Long orderId) {
        return paymentService.queryPayStatus(orderId);
    }

    /**
     * 申请退款（仅限已支付未核销订单，原路退回）
     */
    @PostMapping("/refund/{orderId}")
    public Result<Void> refund(@PathVariable("orderId") Long orderId) {
        return paymentService.refund(orderId);
    }
}
