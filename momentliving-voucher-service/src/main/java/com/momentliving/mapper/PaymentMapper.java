package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 支付流水 Mapper（一单一笔流水，uk_order_id 防重）
 * 状态机：0待支付 1已支付 2已退款 3支付失败
 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {

    /** 回调确认到账：待支付(0) → 已支付(1)。rows=0 且流水已是 1 即重复回调（幂等通过） */
    @Update("update payment set status = 1, transaction_id = #{transactionId}, " +
            "notify_content = #{notifyContent}, pay_time = now() " +
            "where order_id = #{orderId} and status = 0")
    int casMarkSuccess(@Param("orderId") Long orderId,
                       @Param("transactionId") String transactionId,
                       @Param("notifyContent") String notifyContent);

    /** 退款完成：已支付(1) → 已退款(2) */
    @Update("update payment set status = 2 where order_id = #{orderId} and status = 1")
    int casMarkRefunded(Long orderId);

    /** 置为支付失败（长期未付被关单时由对账逻辑使用）：待支付(0) → 支付失败(3) */
    @Update("update payment set status = 3 where order_id = #{orderId} and status = 0")
    int casMarkFailed(Long orderId);
}
