package com.momentliving.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.momentliving.config.RabbitMQConfig;
import com.momentliving.api.client.UserClient;
import com.momentliving.config.RedisIdWorker;

import com.momentliving.constant.RedisConstants;
import com.momentliving.constant.SystemConstants;
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
import com.momentliving.service.VoucherOrderService;
import com.momentliving.vo.FootprintItemVO;
import com.momentliving.vo.FootprintSettingVO;
import jakarta.annotation.Resource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@Slf4j
public class VoucherOrderServiceImpl implements VoucherOrderService {
    @Resource
    private SeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private VoucherOrderMapper voucherOrderMapper;
    @Resource
    private VoucherMapper voucherMapper;
    @Resource
    private UserClient userClient;
    @Resource
    private VoucherVerifyMapper voucherVerifyMapper;
    //RabbitMQ 消息发送工具
    @Resource
    private RabbitTemplate rabbitTemplate;


//加载lua脚本（注意：spring-data-redis 的 Lua 返回类型只支持 Long/Boolean/List，
// 用 Integer 会被映射成 VALUE 输出，Redis 返回整数时 Lettuce 报 ValueOutput does not support set(long)）
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

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

    @Override
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

            //执行lua脚本
            Long res = stringRedisTemplate.execute(
                    SECKILL_SCRIPT,
                    Collections.emptyList(),
                    voucherId.toString(), userId.toString(), RedisConstants.SECKILL_LIMIT.toString()
            );
            if (res != 0) {
                return Result.error(res == 1 ? "库存不足" :"用户下单超过限制，每个人最多只能下单"+RedisConstants.SECKILL_LIMIT+"次");
            }
            //下单成功，将订单信息传递给RabbitMQ队列并且返回订单 id
            //生成订单信息
            long orderId = redisIdWorker.nextId("order");
            VoucherOrder voucherOrder = VoucherOrder.builder()
                    .id(orderId)
                    .userId(userId)
                    .voucherId(voucherId)
                    .payType(0)
                    .status(0)
                    .createTime(LocalDateTime.now())
                    .build();

        // 参数：交换机名称、RoutingKey、消息体；CorrelationData 用于 publisher-confirm 关联业务单号
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_EXCHANGE,
                RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY,
                voucherOrder,
                new org.springframework.amqp.rabbit.connection.CorrelationData(String.valueOf(orderId))
        );
            return Result.success(orderId);
        }


    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // DB 兜底校验一人一单（防止 Redis 和 DB 不一致）
        LambdaQueryWrapper<VoucherOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VoucherOrder::getUserId, userId).eq(VoucherOrder::getVoucherId, voucherId);
        Long count = voucherOrderMapper.selectCount(wrapper);
        //判断
        if (count >= RedisConstants.SECKILL_LIMIT) {
            // 已经下过单了，直接返回（幂等性：重复消费不重复下单）
            log.warn("用户已下单，幂等返回，userId={}, voucherId={}", userId, voucherId);
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
