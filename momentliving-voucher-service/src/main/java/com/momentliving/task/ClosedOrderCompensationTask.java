package com.momentliving.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.momentliving.constant.OrderStatus;
import com.momentliving.entity.VoucherOrder;
import com.momentliving.mapper.VoucherOrderMapper;
import com.momentliving.service.SeckillReservationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** 关单后 Redis 短暂不可用时，按 MySQL 真源重复执行幂等 Lua 补偿。 */
@Slf4j
@Component
public class ClosedOrderCompensationTask {
    private final VoucherOrderMapper orderMapper;
    private final SeckillReservationService reservationService;

    public ClosedOrderCompensationTask(VoucherOrderMapper orderMapper,
                                       SeckillReservationService reservationService) {
        this.orderMapper = orderMapper;
        this.reservationService = reservationService;
    }

    /** 每 30s 扫描 7 天内已关闭的秒杀订单，重放幂等补偿（库存+1/限购-1/删预占） */
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void compensateRecentClosedOrders() {
        List<VoucherOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getStatus, OrderStatus.CLOSED)
                .isNotNull(VoucherOrder::getPurchaseSlot)
                .ge(VoucherOrder::getCreateTime, LocalDateTime.now().minusDays(7))
                .select(VoucherOrder::getId, VoucherOrder::getUserId, VoucherOrder::getVoucherId));
        for (VoucherOrder order : orders) {
            try {
                // 幂等凭据真源在 MySQL：先 CAS 抢标记（0→1），抢到才执行 Lua 回补。
                // Redis 重启丢失数据后，Lua 内的 Redis marker 一并消失，若只靠它防重放，
                // 会与对账任务的"key 丢失按真源重建"叠加造成库存超补（2026-11-20 事故）。
                // 此处 CAS 的 rows=0 直接跳过；rows=1 后 Lua 失败则由对账任务按真源重建收敛。
                if (orderMapper.markRedisCompensated(order.getId()) <= 0) {
                    continue;
                }
                reservationService.compensate(order.getVoucherId(), order.getUserId(), order.getId());
            } catch (Exception e) {
                log.error("已关闭订单 Redis 补偿失败，orderId={}", order.getId(), e);
            }
        }
    }
}
