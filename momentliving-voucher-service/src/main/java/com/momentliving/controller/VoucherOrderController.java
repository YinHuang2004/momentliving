package com.momentliving.controller;

import com.momentliving.constant.SystemConstants;
import com.momentliving.entity.VoucherOrder;
import com.momentliving.result.Result;
import com.momentliving.vo.FootprintItemVO;
import com.momentliving.service.VoucherOrderService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private VoucherOrderService voucherOrderService;

    /**
     * 秒杀下单
     */
    @PostMapping("seckill/{id}")
    public Result<Long> seckillVoucher(@PathVariable("id") Long voucherId) throws InterruptedException {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    /**
     * 普通券下单（同步落库；秒杀券走 seckill/{id}）
     */
    @PostMapping("buy/{id}")
    public Result<Long> buyVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.createBuyOrder(voucherId);
    }

    /**
     * 我的订单列表（分页；status 可选：0未支付 1已支付 2已核销 3已退款）
     */
    @GetMapping("/my")
    public Result<List<VoucherOrder>> queryMyOrders(@RequestParam(value = "current", defaultValue = "1") Integer current,
                                   @RequestParam(value = "pageSize", required = false) Integer pageSize,
                                   @RequestParam(value = "status", required = false) Integer status) {
        return Result.success(voucherOrderService.queryMyOrders(current,
                pageSize != null ? pageSize : SystemConstants.MAX_PAGE_SIZE, status));
    }

    /**
     * 订单详情（仅本人可见）
     */
    @GetMapping("/{id}")
    public Result<VoucherOrder> queryOrderById(@PathVariable("id") Long id) {
        VoucherOrder order = voucherOrderService.getOrderById(id);
        return order == null ? Result.error("订单不存在") : Result.success(order);
    }

    /**
     * 某用户的足迹（购物记录，个人主页"足迹"Tab）：
     * 本人查看不受限；他人查看需足迹开关可见，且只返回清空足迹之后的记录
     */
    @GetMapping("/of/user/{userId}")
    public Result<List<FootprintItemVO>> queryUserFootprint(@PathVariable("userId") Long userId,
                                                            @RequestParam(value = "current", defaultValue = "1") Integer current,
                                                            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return Result.success(voucherOrderService.queryUserFootprint(userId, current, pageSize));
    }
}
