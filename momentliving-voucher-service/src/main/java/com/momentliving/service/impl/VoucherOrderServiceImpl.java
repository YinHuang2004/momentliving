package com.momentliving.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.momentliving.api.client.UserClient;
import com.momentliving.config.RedisIdWorker;

import com.momentliving.constant.RedisConstants;
import com.momentliving.constant.OrderStatus;
import com.momentliving.consumer.OrderCloseHandler;
import com.momentliving.context.UserHolder;
import com.momentliving.entity.SeckillVoucher;
import com.momentliving.entity.Voucher;
import com.momentliving.entity.VoucherOrder;
import com.momentliving.entity.VoucherVerify;

import com.momentliving.mapper.VoucherMapper;
import com.momentliving.mapper.VoucherOrderMapper;
import com.momentliving.mapper.VoucherVerifyMapper;
import com.momentliving.exception.BadRequestException;
import com.momentliving.result.Result;
import com.momentliving.service.SeckillVoucherService;
import com.momentliving.service.SeckillMqPublisher;
import com.momentliving.service.SeckillReservationService;
import com.momentliving.service.VoucherOrderService;
import com.momentliving.vo.FootprintItemVO;
import com.momentliving.vo.FootprintSettingVO;
import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 券订单实现：秒杀走 Lua 预扣预占 + MQ 异步落库（Redis 预占闭环，失败由对账任务兜底），
 * 普通券同步落库；查询类接口负责回填核销码/券标题
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl implements VoucherOrderService {
    @Resource
    private SeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private VoucherOrderMapper voucherOrderMapper;
    @Resource
    private VoucherMapper voucherMapper;
    @Resource
    private UserClient userClient;
    @Resource
    private VoucherVerifyMapper voucherVerifyMapper;
    @Resource
    private SeckillReservationService reservationService;
    @Resource
    private SeckillMqPublisher seckillMqPublisher;

    /** 关单公共逻辑（订单锁 + CAS + 库存回补），用户主动取消与超时关单共用同一套 */
    @Resource
    private OrderCloseHandler orderCloseHandler;

    /**
     * 普通券下单：同步落库（低并发无需 MQ），秒杀券引导走抢购入口。
     * 普通券无 seckill_voucher 库存记录，不限制购数量。
     */
    @Override
    public Result<Long> createBuyOrder(Long voucherId) {
        Voucher voucher = voucherMapper.selectById(voucherId);
        if (voucher == null) {
            return Result.error("优惠券不存在");
        }
        if (voucher.getType() != null && voucher.getType() == 1) {
            return Result.error("秒杀券请通过抢购入口下单");
        }
        Long userId = UserHolder.getUser().getId();
        VoucherOrder order = VoucherOrder.builder()
                .id(redisIdWorker.nextId("order"))
                .userId(userId)
                .voucherId(voucherId)
                .payType(0)
                .status(0)
                .createTime(LocalDateTime.now())
                .build();
        voucherOrderMapper.insert(order);
        return Result.success(order.getId());
    }

    /**
     * 秒杀下单主链路：Lua 原子预扣预占 → 发 MQ 同步等 Confirm →
     * 按发送结果把预占标记为 CONFIRMED/PENDING（PENDING 由对账任务重投）
     */
    @Override
    @Transactional
    public Result<Long> seckillVoucher(Long voucherId) {
        //查询秒杀券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.error("秒杀券不存在");
        }
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.error("秒杀券未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.error("秒杀券已结束");
        }
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        long reserveResult = reservationService.reserve(
                voucherId, userId, RedisConstants.SECKILL_LIMIT, orderId);
        if (reserveResult == -3) {
            return Result.error("秒杀库存尚未初始化，请稍后重试");
        }
        if (reserveResult == -1) {
            return Result.error("库存不足");
        }
        if (reserveResult == -2) {
            return Result.error("用户下单超过限制，每个人最多只能下单" + RedisConstants.SECKILL_LIMIT + "次");
        }

        VoucherOrder voucherOrder = VoucherOrder.builder()
                .id(orderId)
                .userId(userId)
                .voucherId(voucherId)
                .purchaseSlot((int) reserveResult)
                .payType(0)
                .status(0)
                .createTime(LocalDateTime.now())
                .build();
        boolean confirmed = seckillMqPublisher.publish(voucherOrder);
        reservationService.updateAttempt(
                voucherId,
                orderId,
                new SeckillReservationService.Reservation(
                        userId, (int) reserveResult, System.currentTimeMillis(), "PENDING", 0),
                confirmed ? "CONFIRMED" : "PENDING",
                1);
        if (!confirmed) {
            log.warn("MQ 发送结果不确定，订单保留在 Redis 等待恢复任务，orderId={}", orderId);
        }
        return Result.success(orderId);
    }


    /** 消费侧订单落库：orderId 判重 → DB 限购校验 → CAS 扣库存 → 插入订单 → 事务提交后删预占 */
    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // ACK 丢失或消费者重启会造成重复投递；先按 orderId 判重，避免重复扣 DB 库存。
        if (voucherOrderMapper.selectById(voucherOrder.getId()) != null) {
            registerReservationCleanup(voucherId, voucherOrder.getId());
            return;
        }
        // DB 兜底校验一人一单（防止 Redis 和 DB 不一致）
        LambdaQueryWrapper<VoucherOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VoucherOrder::getUserId, userId).eq(VoucherOrder::getVoucherId, voucherId);
        Long count = voucherOrderMapper.selectCount(wrapper);
        //判断
        if (count >= RedisConstants.SECKILL_LIMIT) {
            // 已经下过单了，直接返回（幂等性：重复消费不重复下单）
            log.warn("用户已下单，幂等返回，userId={}, voucherId={}", userId, voucherId);
            reservationService.compensate(voucherId, userId, voucherOrder.getId());
            return;
        }

        // CAS 减少库存，防止超卖
        int rows = seckillVoucherService.deductStock(voucherId);
        if (rows <= 0) {
            // 库存不足，抛异常 → 消费者 catch 到 → NACK → 进死信队列 → Redis 补偿
            throw new RuntimeException("库存不足，扣减失败，voucherId=" + voucherId);
        }


        // 插入订单（直接补全传入对象的缺失字段，无需新建对象）
        voucherOrder.setPayType(0);
        voucherOrder.setStatus(0);
        voucherOrder.setCreateTime(LocalDateTime.now());
        voucherOrderMapper.insert(voucherOrder);
        registerReservationCleanup(voucherId, voucherOrder.getId());
    }

    /** 事务提交后再删 Redis 预占（先落库后删，防删了预占订单却没落库）；无事务则立即删 */
    private void registerReservationCleanup(Long voucherId, Long orderId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            reservationService.finish(voucherId, orderId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    reservationService.finish(voucherId, orderId);
                } catch (Exception e) {
                    log.error("订单已提交但 Redis 预约记录清理失败，将由对账任务处理，orderId={}", orderId, e);
                }
            }
        });
    }

    @Override
    public Result<Void> cancelOrder(Long orderId) {
        Long userId = UserHolder.getUser().getId();
        VoucherOrder order = voucherOrderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BadRequestException("订单不存在");
        }
        int status = order.getStatus() == null ? -1 : order.getStatus();
        if (status == OrderStatus.PAID) {
            throw new BadRequestException("订单已支付，如需取消请申请退款");
        }
        if (status != OrderStatus.PENDING_PAY) {
            throw new BadRequestException("当前订单状态不支持取消");
        }
        // 复用关单公共逻辑：与 TTL 死信/定时扫描完全同一条路径——
        // 订单级锁 tryLock(3,10) → casClose 0→4 → DB 回补库存 → Redis 回补(库存+1/限购-1) → 流水置失败
        boolean closed = orderCloseHandler.closeUnpaidOrder(orderId);
        if (!closed) {
            // false = 锁竞争失败或状态刚被流转；重查一次给用户准确反馈
            VoucherOrder latest = voucherOrderMapper.selectById(orderId);
            if (latest != null && latest.getStatus() != null
                    && latest.getStatus() == OrderStatus.PENDING_PAY) {
                throw new BadRequestException("系统繁忙，请稍后重试");
            }
            throw new BadRequestException("订单状态已变化，请刷新查看");
        }
        log.info("用户主动取消订单，库存与限购资格已回补，orderId={}, userId={}", orderId, userId);
        return Result.success();
    }

    @Override
    public List<VoucherOrder> queryMyOrders(Integer current, Integer pageSize, Integer status) {
        Long userId = UserHolder.getUser().getId();
        LambdaQueryWrapper<VoucherOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VoucherOrder::getUserId, userId)
                // status 传 null 表示查询全部状态
                .eq(status != null, VoucherOrder::getStatus, status)
                .orderByDesc(VoucherOrder::getCreateTime);
        List<VoucherOrder> orders = voucherOrderMapper.selectPage(new Page<>(current, pageSize), wrapper).getRecords();
        fillVerifyCodes(orders);
        return orders;
    }

    @Override
    public VoucherOrder getOrderById(Long id) {
        Long userId = UserHolder.getUser().getId();
        VoucherOrder order = voucherOrderMapper.selectById(id);
        // 订单不存在或非本人订单，统一返回 null（防止越权查询他人订单）
        if (order == null || !order.getUserId().equals(userId)) {
            return null;
        }
        fillVerifyCodes(Collections.singletonList(order));
        return order;
    }

    /**
     * 从 voucher_verify 表批量回填 16 位核销码（一个订单支付成功后有且只有一条核销记录）。
     * 用户端"订单详情/我的券包"出示核销码给商家核销依赖此字段。
     */
    private void fillVerifyCodes(List<VoucherOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        List<Long> orderIds = orders.stream().map(VoucherOrder::getId).collect(Collectors.toList());
        Map<Long, String> codeMap = voucherVerifyMapper.selectList(new LambdaQueryWrapper<VoucherVerify>()
                        .in(VoucherVerify::getOrderId, orderIds))
                .stream()
                .collect(Collectors.toMap(VoucherVerify::getOrderId, VoucherVerify::getVerifyCode, (a, b) -> a));
        orders.forEach(order -> order.setVerifyCode(codeMap.get(order.getId())));
    }

    @Override
    public List<FootprintItemVO> queryUserFootprint(Long targetUserId, Integer current, Integer pageSize) {
        Long viewerId = UserHolder.getUser().getId();
        LambdaQueryWrapper<VoucherOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VoucherOrder::getUserId, targetUserId)
                // 足迹只展示已支付(1)/已核销(2)的购物记录
                .in(VoucherOrder::getStatus, 1, 2)
                .orderByDesc(VoucherOrder::getPayTime);

        // 他人查看：经 UserClient 远程查询足迹设置（user-service 是足迹数据的唯一写入方），
        // 受可见开关限制，且只展示"清空足迹"之后的记录
        if (!targetUserId.equals(viewerId)) {
            FootprintSettingVO settings = userClient.getFootprintSettings(targetUserId).getData();
            if (settings == null || !Boolean.TRUE.equals(settings.getVisible())) {
                throw new BadRequestException("该用户已隐藏足迹");
            }
            if (settings.getClearedTime() != null && settings.getClearedTime() > 0) {
                LocalDateTime clearedTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(settings.getClearedTime()), ZoneId.systemDefault());
                wrapper.gt(VoucherOrder::getCreateTime, clearedTime);
            }
        }

        List<VoucherOrder> orders = voucherOrderMapper.selectPage(
                new Page<>(current, pageSize), wrapper).getRecords();
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }
        // 批量回填券标题
        Map<Long, Voucher> voucherMap = voucherMapper.selectBatchIds(
                        orders.stream().map(VoucherOrder::getVoucherId).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(Voucher::getId, v -> v));
        return orders.stream().map(order -> {
            Voucher voucher = voucherMap.get(order.getVoucherId());
            return FootprintItemVO.builder()
                    .orderId(order.getId())
                    .voucherId(order.getVoucherId())
                    .voucherTitle(voucher != null ? voucher.getTitle() : null)
                    .status(order.getStatus())
                    .payTime(order.getPayTime())
                    .createTime(order.getCreateTime())
                    .build();
        }).collect(Collectors.toList());
    }
}
