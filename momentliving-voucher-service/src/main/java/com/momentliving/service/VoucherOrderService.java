package com.momentliving.service;

import com.momentliving.entity.VoucherOrder;
import com.momentliving.result.Result;
import com.momentliving.vo.FootprintItemVO;

import java.util.List;

/** 券订单服务：秒杀（Lua 预扣 + MQ 异步落库）/普通下单、订单查询、取消与足迹 */
public interface VoucherOrderService {

    /** 秒杀下单入口：Lua 原子预扣预占成功后发 MQ，由消费者异步创建订单 */
    Result<Long> seckillVoucher(Long voucherId) throws InterruptedException;

    /**
     * 普通券下单（同步落库，无库存/限购限制；秒杀券请走 seckillVoucher）
     */
    Result<Long> createBuyOrder(Long voucherId);

    /** MQ 消费侧落库：判重 + 限购校验 + CAS 扣库存 + 插入订单 */
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
     * 用户主动取消订单（仅限待支付）：
     * 复用关单公共逻辑（订单锁 + CAS 关单 0→4 + DB/Redis 库存回补 + 流水置失败），
     * 取消后秒杀库存与限购资格立即释放，无需等 30 分钟超时关单
     */
    Result<Void> cancelOrder(Long orderId);

    /**
     * 某用户的足迹（购物记录，个人主页展示）：
     * 本人直接查；他人需足迹开关可见，且只返回清空时间戳之后的已支付/已核销记录
     */
    List<FootprintItemVO> queryUserFootprint(Long targetUserId, Integer current, Integer pageSize);
}
