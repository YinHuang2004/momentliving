package com.momentliving.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.momentliving.constant.OrderStatus;
import com.momentliving.entity.Payment;
import com.momentliving.entity.Voucher;
import com.momentliving.entity.VoucherOrder;
import com.momentliving.exception.BadRequestException;
import com.momentliving.exception.PayException;
import com.momentliving.context.UserHolder;
import com.momentliving.mapper.PaymentMapper;
import com.momentliving.mapper.VoucherMapper;
import com.momentliving.mapper.VoucherOrderMapper;
import com.momentliving.pay.AliPayProvider;
import com.momentliving.pay.PayProvider;
import com.momentliving.result.Result;
import com.momentliving.service.PaymentService;
import com.momentliving.service.VerifyService;
import com.momentliving.vo.PayOrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单支付实现。
 *
 * <p>状态机（见 {@link OrderStatus}）：
 * 待支付(0) --回调确认--> 已支付(1) --核销--> 已核销(2)
 *                         已支付(1) --退款--> 已退款(3)
 * 待支付(0) --超时--> 已关闭(4)
 *
 * <p>幂等设计：
 * - payment 表 uk_order_id：一单一笔流水，重复创建支付直接复用；
 * - 回调处理全部走条件 UPDATE（CAS），重复回调二次进入时直接返回成功。
 */
@Service
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    @Resource
    private VoucherOrderMapper voucherOrderMapper;
    @Resource
    private VoucherMapper voucherMapper;
    @Resource
    private PaymentMapper paymentMapper;
    @Resource
    private VerifyService verifyService;
    /** Spring 注入所有 PayProvider 实现（AliPay/WechatPay/Mock），按 payType 路由 */
    @Resource
    private List<PayProvider> providers;

    // ==================== 发起支付 ====================

    @Override
    public Result<PayOrderVO> createPayment(Long orderId, Integer payType) {
        Long userId = UserHolder.getUser().getId();
        if (payType == null || payType < 1 || payType > 3) {
            throw new BadRequestException("不支持的支付方式");
        }
        VoucherOrder order = voucherOrderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BadRequestException("订单不存在");
        }
        int status = order.getStatus() == null ? -1 : order.getStatus();
        if (status == OrderStatus.CLOSED) {
            throw new BadRequestException("订单已超时关闭，请重新下单");
        }
        if (status != OrderStatus.PENDING_PAY) {
            throw new BadRequestException("当前订单状态不允许支付");
        }
        PayProvider provider = providers.stream()
                .filter(p -> p.supportPayType() == payType)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("该支付方式暂不可用"));

        // 券价格是「分」，转成元的两位小数
        BigDecimal amount = loadAmountYuan(order.getVoucherId());
        String subject = loadSubject(order.getVoucherId());

        // 防"连点":已存在待支付流水则复用（uk_order_id + DuplicateKey 兜底并发首次创建）
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, orderId));
        if (payment == null) {
            try {
                paymentMapper.insert(Payment.builder()
                        .orderId(orderId).userId(userId).amount(amount)
                        .payType(payType).status(0)
                        .createTime(LocalDateTime.now()).build());
            } catch (DuplicateKeyException e) {
                log.info("支付流水并发插入冲突，复用已有流水，orderId={}", orderId);
            }
        }

        String payContent = provider.prepay(order, amount, subject);
        return Result.success(PayOrderVO.builder()
                .orderId(orderId).payType(payType)
                .orderStatus(OrderStatus.PENDING_PAY).amount(amount)
                .payContent(payContent).build());
    }

    // ==================== 回调处理 ====================

    @Override
    public AliNotifyPayload handleAlipayNotify(Map<String, String> params) {
        try {
            AliPayProvider.NotifyPayload payload = aliProvider().verifyNotify(params);
            // 金额一致性校验：回调金额必须与券价格一致（防"1分钱买大额券"式篡改）
            VoucherOrder order = voucherOrderMapper.selectById(payload.orderId());
            if (order == null) {
                log.error("回调对应订单不存在，orderId={}", payload.orderId());
                throw new PayException("订单不存在");
            }
            BigDecimal expect = loadAmountYuan(order.getVoucherId());
            if (payload.totalAmount() == null || payload.totalAmount().compareTo(expect) != 0) {
                log.error("支付宝回调金额与订单应付金额不一致！orderId={}, 回调={}, 应付={}",
                        payload.orderId(), payload.totalAmount(), expect);
                throw new PayException("回调金额不一致");
            }
            return new AliNotifyPayload(payload.orderId(), payload.tradeNo(), payload.totalAmount());
        } catch (com.alipay.api.AlipayApiException e) {
            throw new PayException("支付宝验签异常");
        }
    }

    @Override
    @Transactional
    public boolean processPaid(Long orderId, String transactionId, String rawNotify) {
        VoucherOrder order = voucherOrderMapper.selectById(orderId);
        if (order == null) {
            log.error("收到未知订单的支付回调，orderId={}, transactionId={}", orderId, transactionId);
            return false;
        }
        // 1. 流水 CAS：待支付(0) → 已支付(1)。rows=0 时检查是否已是已支付（重放通知）
        int rows = paymentMapper.casMarkSuccess(orderId, transactionId, truncateNotify(rawNotify));
        if (rows <= 0) {
            Payment current = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                    .eq(Payment::getOrderId, orderId));
            if (current != null && current.getStatus() != null && current.getStatus() == 1) {
                log.info("支付回调重放（流水已确认），幂等返回 success，orderId={}", orderId);
                return true;
            }
            log.warn("支付流水量状态非待支付，拒绝处理，orderId={}, 流水状态={}",
                    orderId, current == null ? null : current.getStatus());
            return false;
        }

        // 2. 订单 CAS：待支付(0) → 已支付(1)，并同步生成核销码
        rows = voucherOrderMapper.casMarkPaid(orderId);
        if (rows > 0) {
            verifyService.createVerifyCode(order);
            log.info("支付成功，orderId={}, transactionId={}", orderId, transactionId);
            return true;
        }

        // 3. 竞态：用户付款瞬间订单被超时关单。不能吞掉 —— 抛异常让事务回滚并返回 failure，
        //    渠道会按协议继续重试，等对账/人工退款介入后自然收敛
        log.error("支付与关单竞态冲突！钱已到账但订单已关闭，orderId={}, transactionId={}", orderId, transactionId);
        throw new PayException("订单支付状态冲突（已关闭），需人工核对退款");
    }

    // ==================== 退款 ====================

    @Override
    @Transactional
    public Result<Void> refund(Long orderId) {
        Long userId = UserHolder.getUser().getId();
        VoucherOrder order = voucherOrderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BadRequestException("订单不存在");
        }
        if (order.getStatus() == null || order.getStatus() != OrderStatus.PAID) {
            throw new BadRequestException("仅「已支付未核销」的订单可以退款");
        }
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, orderId));
        if (payment == null || payment.getStatus() == null || payment.getStatus() != 1) {
            throw new BadRequestException("支付流水状态异常，无法退款");
        }
        PayProvider provider = providers.stream()
                .filter(p -> p.supportPayType() == payment.getPayType())
                .findFirst()
                .orElseThrow(() -> new PayException("原支付渠道不可用，无法原路退款"));
        provider.refund(orderId, payment.getAmount(), "用户申请退款");

        // 渠道退回成功后流转两个状态（同一事务）
        if (voucherOrderMapper.casMarkRefunded(orderId) <= 0) {
            throw new BadRequestException("订单状态已变化，请刷新后重试");
        }
        paymentMapper.casMarkRefunded(orderId);
        log.info("退款完成，orderId={}, amount={}", orderId, payment.getAmount());
        return Result.success();
    }

    // ==================== 查询 ====================

    @Override
    public Result<Map<String, Object>> queryPayStatus(Long orderId) {
        Long userId = UserHolder.getUser().getId();
        VoucherOrder order = voucherOrderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BadRequestException("订单不存在");
        }
        Map<String, Object> data = new HashMap<>(4);
        data.put("orderId", orderId);
        data.put("orderStatus", order.getStatus());
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, orderId));
        data.put("payStatus", payment == null ? null : payment.getStatus());
        return Result.success(data);
    }

    // ==================== 私有工具 ====================

    private BigDecimal loadAmountYuan(Long voucherId) {
        Voucher voucher = voucherMapper.selectById(voucherId);
        if (voucher == null || voucher.getPayValue() == null) {
            throw new PayException("券信息异常，无法计算应付金额");
        }
        return BigDecimal.valueOf(voucher.getPayValue())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private String loadSubject(Long voucherId) {
        Voucher voucher = voucherMapper.selectById(voucherId);
        String title = voucher.getTitle();
        return title == null ? "一刻生活优惠券" : title;
    }

    private AliPayProvider aliProvider() {
        return providers.stream()
                .filter(AliPayProvider.class::isInstance)
                .map(AliPayProvider.class::cast)
                .findFirst()
                .orElseThrow(() -> new PayException("支付宝渠道未注册"));
    }

    private static String truncateNotify(String raw) {
        if (raw == null) {
            return null;
        }
        // notify_content 是 TEXT 上限 64KB，留余量截断
        return raw.length() > 60000 ? raw.substring(0, 60000) : raw;
    }
}
