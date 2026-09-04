package com.momentliving.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.momentliving.constant.OrderStatus;
import com.momentliving.constant.RedisConstants;
import com.momentliving.entity.VoucherOrder;
import com.momentliving.entity.VoucherShop;
import com.momentliving.entity.VoucherVerify;
import com.momentliving.exception.BadRequestException;
import com.momentliving.mapper.VoucherOrderMapper;
import com.momentliving.mapper.VoucherVerifyMapper;
import com.momentliving.service.VerifyService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.momentliving.vo.MerchantStatsVO;
import com.momentliving.vo.RecentVerifyVO;
import com.momentliving.vo.TodayStats;
import com.momentliving.vo.VerifyOrderPreviewVO;
import com.momentliving.vo.VerifyRecordsVO;

/**
 * 券核销实现：
 * 1. 核销码生成 —— SHA256(orderId + 固定盐) 取前 16 位 hex，确定性生成，
 *    同一订单无论回调重放多少次都得到同一个码（天然幂等），uk_verify_code 双保险；
 * 2. Redis 预热 verify:code:{code} → orderId，商家扫码先查缓存再落 DB；
 * 3. 核销动作 —— Redisson 锁码 + 订单 CAS(1→2) + 核销记录 CAS(0→1)，同一事务提交。
 */
@Service
@Slf4j
public class VerifyServiceImpl implements VerifyService {

    /** 码盐值：仅用于让不同部署环境的码空间独立，不承担安全职责 */
    private static final String CODE_SALT = "momentliving-voucher-verify";

    @Resource
    private VoucherVerifyMapper voucherVerifyMapper;
    @Resource
    private VoucherOrderMapper voucherOrderMapper;
    @Resource
    private com.momentliving.mapper.VoucherMapper voucherMapper;
    @Resource
    private com.momentliving.mapper.VoucherShopMapper voucherShopMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    @Override
    public void createVerifyCode(VoucherOrder order) {
        String code = generateCode(order.getId());
        Long shopId = 0L;
        try {
            // 🆕 单店券（voucher_shop 仅 1 条）可预知核销店铺，直接预填归属；
            //    多店/全场通用券核销店铺不定，落 0，核销成功时回填实际核销店铺
            List<VoucherShop> scope = voucherShopMapper.selectList(
                    new LambdaQueryWrapper<VoucherShop>().eq(VoucherShop::getVoucherId, order.getVoucherId()));
            if (scope.size() == 1) {
                shopId = scope.get(0).getShopId();
            }
        } catch (Exception e) {
            log.warn("查询券适用范围失败，shopId 置 0，orderId={}", order.getId(), e);
        }
        try {
            voucherVerifyMapper.insert(VoucherVerify.builder()
                    .orderId(order.getId())
                    .userId(order.getUserId())
                    .shopId(shopId)
                    .verifyCode(code)
                    .status(0)
                    .createTime(java.time.LocalDateTime.now())
                    .build());
        } catch (DuplicateKeyException e) {
            // uk_order_id / uk_verify_code 命中 = 回调重放，幂等通过
            log.info("核销记录已存在（幂等跳过），orderId={}", order.getId());
            return;
        }
        // Redis 预热：code → orderId
        stringRedisTemplate.opsForValue().set(RedisConstants.VERIFY_CODE_KEY + code,
                order.getId().toString(), RedisConstants.VERIFY_CODE_TTL, TimeUnit.SECONDS);
        log.info("核销码生成成功，orderId={}, shopId={}", order.getId(), shopId);
    }

    @Override
    public MerchantStatsVO stats(Long shopId) {
        if (shopId == null) {
            throw new BadRequestException("缺少店铺参数");
        }
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        // 1. 今日概览
        TodayStats today = TodayStats.builder()
                .pendingVerify(voucherOrderMapper.countPendingVerify(shopId))
                .verified(voucherVerifyMapper.countVerifiedSince(shopId, todayStart))
                .revenue(BigDecimal.valueOf(voucherVerifyMapper.sumRevenueFenSince(shopId, todayStart))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                .build();
        // 2. 最近核销（最多 10 条，昵称由 admin-service 回填）
        List<RecentVerifyVO> recent = voucherVerifyMapper.selectRecentVerified(shopId, 10);
        return MerchantStatsVO.builder()
                .today(today)
                .recent(recent)
                .build();
    }

    /**
     * 核销记录分页：按店铺 + 状态筛选，核销时间倒序。
     * 券名批量回填（订单→券两次批量查询，避免 N+1），买家昵称由 admin-service 编排回填。
     */
    @Override
    public VerifyRecordsVO pageRecords(Long shopId, Integer status, Integer current, Integer pageSize) {
        if (shopId == null) {
            throw new BadRequestException("缺少店铺参数");
        }
        Page<VoucherVerify> page = voucherVerifyMapper.selectPage(
                new Page<>(current == null ? 1 : current, pageSize == null ? 10 : pageSize),
                new LambdaQueryWrapper<VoucherVerify>()
                        .eq(VoucherVerify::getShopId, shopId)
                        .eq(status != null, VoucherVerify::getStatus, status)
                        .orderByDesc(VoucherVerify::getVerifyTime)
                        .orderByDesc(VoucherVerify::getId));
        List<VoucherVerify> records = page.getRecords();
        if (records.isEmpty()) {
            return VerifyRecordsVO.builder().total(page.getTotal()).list(Collections.emptyList()).build();
        }
        // 批量补券名：订单 id 集合 → 订单 → 券 id 集合 → 券
        List<Long> orderIds = records.stream().map(VoucherVerify::getOrderId).collect(Collectors.toList());
        Map<Long, VoucherOrder> orderMap = voucherOrderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(VoucherOrder::getId, o -> o, (a, b) -> a));
        List<Long> voucherIds = orderMap.values().stream()
                .map(VoucherOrder::getVoucherId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, com.momentliving.entity.Voucher> voucherMap = voucherIds.isEmpty() ? Collections.emptyMap()
                : voucherMapper.selectBatchIds(voucherIds).stream()
                        .collect(Collectors.toMap(com.momentliving.entity.Voucher::getId, v -> v, (a, b) -> a));
        List<RecentVerifyVO> list = records.stream().map(vv -> {
            VoucherOrder order = orderMap.get(vv.getOrderId());
            com.momentliving.entity.Voucher voucher = order == null ? null : voucherMap.get(order.getVoucherId());
            String code = vv.getVerifyCode();
            return RecentVerifyVO.builder()
                    .orderId(vv.getOrderId())
                    .voucherTitle(voucher == null ? null : voucher.getTitle())
                    .userId(vv.getUserId())
                    .verifyTime(vv.getVerifyTime())
                    .status(vv.getStatus())
                    .verifyCodeTail(code == null ? null : code.substring(Math.max(0, code.length() - 4)))
                    .build();
        }).collect(Collectors.toList());
        return VerifyRecordsVO.builder().total(page.getTotal()).list(list).build();
    }

    @Override
    @Transactional
    public void verifyByCode(String verifyCode, Long merchantId, Long shopId) {
        if (verifyCode == null || verifyCode.isBlank()) {
            throw new BadRequestException("核销码不能为空");
        }
        String code = verifyCode.trim();

        RLock lock = redissonClient.getLock(RedisConstants.LOCK_VERIFY_CODE_KEY + code);
        boolean locked;
        try {
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("核销请求繁忙，请重试");
        }
        if (!locked) {
            throw new BadRequestException("核销请求处理中，请勿重复操作");
        }
        try {
            // 1. 查核销记录：先 Redis 定位订单，再走 DB 记录（DB 为准）
            VoucherVerify record = findRecord(code);
            if (record.getStatus() != null && record.getStatus() == 1) {
                throw new BadRequestException("该券已被核销");
            }
            if (record.getStatus() != null && record.getStatus() == 2) {
                throw new BadRequestException("该券已作废，无法核销");
            }

            // 2. 🆕 券适用范围校验：单店券仅限本店、多店券须在名单内、全场通用不限
            checkVoucherScope(record, shopId);

            // 3. 订单状态机：已支付(1) → 已核销(2)
            int rows = voucherOrderMapper.casMarkUsed(record.getOrderId());
            if (rows <= 0) {
                VoucherOrder current = voucherOrderMapper.selectById(record.getOrderId());
                String reason = switch (current == null ? -1 : current.getStatus()) {
                    case OrderStatus.PENDING_PAY -> "订单还未支付";
                    case OrderStatus.USED -> "券已被核销";
                    case OrderStatus.REFUNDED -> "券已退款，无法使用";
                    case OrderStatus.CLOSED -> "订单已超时关闭";
                    default -> "订单状态异常，无法核销";
                };
                throw new BadRequestException(reason);
            }

            // 3. 核销记录 CAS：未核销(0) → 已核销(1)
            rows = voucherVerifyMapper.casVerify(record.getId(), merchantId);
            if (rows <= 0) {
                // 并发兜底（正常被上面的锁挡住）；事务回滚订单状态
                throw new BadRequestException("核销失败：状态已被其他操作改变");
            }

            // 5. 🆕 核销归属回填：全场/多店券核销记录原本 shop_id=0，回填实际核销店铺，
            //    商家端工作台统计/核销记录才能按店铺看到这笔
            if (record.getShopId() == null || record.getShopId() == 0) {
                voucherVerifyMapper.update(null, new LambdaUpdateWrapper<VoucherVerify>()
                        .eq(VoucherVerify::getId, record.getId())
                        .set(VoucherVerify::getShopId, shopId));
            }

            // 6. 已用掉的码下线
            stringRedisTemplate.delete(RedisConstants.VERIFY_CODE_KEY + code);
            log.info("核销成功，orderId={}, merchantId={}, shopId={}", record.getOrderId(), merchantId, shopId);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 按核销码查订单预览（只读）：核销记录 → 订单 → 券，供商家"先核对再确认"。
     * 不做任何状态变更，码无效/订单缺失时抛 BadRequestException。
     */
    @Override
    public VerifyOrderPreviewVO previewByCode(String verifyCode) {
        if (verifyCode == null || verifyCode.isBlank()) {
            throw new BadRequestException("核销码不能为空");
        }
        VoucherVerify record = findRecord(verifyCode.trim());
        VoucherOrder order = voucherOrderMapper.selectById(record.getOrderId());
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }
        com.momentliving.entity.Voucher voucher = order.getVoucherId() == null ? null
                : voucherMapper.selectById(order.getVoucherId());
        return VerifyOrderPreviewVO.builder()
                .orderId(order.getId())
                .voucherId(order.getVoucherId())
                .voucherTitle(voucher == null ? null : voucher.getTitle())
                .userId(order.getUserId())
                .payValue(voucher == null ? null : voucher.getPayValue())
                .status(order.getStatus())
                .verifyStatus(record.getStatus())
                .createTime(order.getCreateTime())
                .build();
    }

    private VoucherVerify findRecord(String code) {
        // 先查缓存定位 orderId，加速扫码场景
        String orderIdStr = stringRedisTemplate.opsForValue().get(RedisConstants.VERIFY_CODE_KEY + code);
        if (orderIdStr != null) {
            VoucherVerify byOrderId = voucherVerifyMapper.selectOne(new LambdaQueryWrapper<VoucherVerify>()
                    .eq(VoucherVerify::getOrderId, Long.valueOf(orderIdStr)));
            if (byOrderId != null && code.equals(byOrderId.getVerifyCode())) {
                return byOrderId;
            }
        }
        // 缓存未命中/不一致：直接按唯一索引查 DB
        VoucherVerify record = voucherVerifyMapper.selectOne(new LambdaQueryWrapper<VoucherVerify>()
                .eq(VoucherVerify::getVerifyCode, code));
        if (record == null) {
            throw new BadRequestException("核销码无效");
        }
        return record;
    }

    /** 确定性核销码：SHA256(salt + orderId) 前 16 位 hex */
    private String generateCode(Long orderId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((CODE_SALT + orderId).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            // 不可能发生（SHA-256 必然存在），兜底转 IllegalStateException
            throw new IllegalStateException(e);
        }
    }

    /**
     * 🆕 券适用范围校验（范围唯一事实源 = voucher_shop）：
     * 无记录 = 全场通用不限；有记录 = 单店/多店名单，核销店铺须在名单内。
     */
    private void checkVoucherScope(VoucherVerify record, Long shopId) {
        if (shopId == null) {
            throw new BadRequestException("缺少核销店铺信息，请重新登录商家端");
        }
        VoucherOrder order = voucherOrderMapper.selectById(record.getOrderId());
        if (order == null) {
            throw new BadRequestException("核销码对应的订单不存在");
        }
        List<VoucherShop> scope = voucherShopMapper.selectList(
                new LambdaQueryWrapper<VoucherShop>().eq(VoucherShop::getVoucherId, order.getVoucherId()));
        if (scope.isEmpty()) {
            return; // 全场通用券：任意店铺可核销
        }
        boolean hit = scope.stream().anyMatch(vs -> shopId.equals(vs.getShopId()));
        if (!hit) {
            String reason = scope.size() == 1
                    ? "该券仅限指定店铺使用，当前店铺无法核销"
                    : "该券不适用于当前店铺，无法核销";
            throw new BadRequestException(reason);
        }
    }
}
