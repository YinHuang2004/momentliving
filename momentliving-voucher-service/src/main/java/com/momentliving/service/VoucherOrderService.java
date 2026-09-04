package com.momentliving.service;

import com.momentliving.entity.VoucherOrder;
import com.momentliving.result.Result;
import com.momentliving.vo.FootprintItemVO;

import java.util.List;

public interface VoucherOrderService {

    Result<Long> seckillVoucher(Long voucherId) throws InterruptedException;

    /**
     * 普通券下单（同步落库，无库存/限购限制；秒杀券请走 seckillVoucher）
     */
    Result<Long> createBuyOrder(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);

    /**
     * 我的订单列表（分页，可按状态过滤，status 传 null 表示全部）
     */
    List<VoucherOrder> queryMyOrders(Integer current, Integer pageSize, Integer status);

    /**
     * 订单详情（仅本人可见，非本人或不存在返回 null）
     */
    VoucherOrder getOrderById(Long id);

    /**
     * 某用户的足迹（购物记录，个人主页展示）：
     * 本人直接查；他人需足迹开关可见，且只返回清空时间戳之后的已支付/已核销记录
     */
    List<FootprintItemVO> queryUserFootprint(Long targetUserId, Integer current, Integer pageSize);
}
