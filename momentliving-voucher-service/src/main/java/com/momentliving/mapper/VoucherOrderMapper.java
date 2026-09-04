package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.VoucherOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 券订单 Mapper
 * 状态机流转统一走下方条件 UPDATE：where status = 期望值，
 * 返回受影响行数 —— rows=1 表示本次流转成功，rows=0 表示状态已被并发请求改变（幂等/拒绝）。
 */
@Mapper
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {

    /** 支付成功回调：待支付(0) → 已支付(1)，同时记录支付时间 */
    @Update("update voucher_order set status = 1, pay_time = now() where id = #{orderId} and status = 0")
    int casMarkPaid(Long orderId);

    /** 商家核销：已支付(1) → 已核销(2)，同时记录使用时间 */
    @Update("update voucher_order set status = 2, use_time = now() where id = #{orderId} and status = 1")
    int casMarkUsed(Long orderId);

    /** 退款完成：已支付(1) → 已退款(3)，同时记录退款时间 */
    @Update("update voucher_order set status = 3, refund_time = now() where id = #{orderId} and status = 1")
    int casMarkRefunded(Long orderId);

    /** 超时关单：待支付(0) → 已关闭(4) */
    @Update("update voucher_order set status = 4 where id = #{orderId} and status = 0")
    int casClose(Long orderId);

    /**
     * 商家端统计：该店铺「已支付(1) 未核销」的待核销订单数。
     * 范围 = voucher_shop（含本店的多店/单店券 + 无记录的全场通用券）
     */
    @Select("select count(*) from voucher_order o " +
            "join voucher v on o.voucher_id = v.id " +
            "where o.status = 1 and (" +
            "  exists (select 1 from voucher_shop vs where vs.voucher_id = v.id and vs.shop_id = #{shopId})" +
            "  or not exists (select 1 from voucher_shop vs where vs.voucher_id = v.id))")
    long countPendingVerify(@Param("shopId") Long shopId);

    /**
     * 用户购买过（已支付 1 / 已核销 2）的店铺 id 去重集合（博客"只能发布买过的店"校验用）。
     * 全场通用券（voucher_shop 无记录）视为对全部店铺有效
     */
    @Select("select distinct s.id from shop s where exists (" +
            "  select 1 from voucher_order o join voucher v on o.voucher_id = v.id " +
            "  where o.user_id = #{userId} and o.status in (1, 2) " +
            "    and (exists (select 1 from voucher_shop vs where vs.voucher_id = v.id and vs.shop_id = s.id)" +
            "      or not exists (select 1 from voucher_shop vs where vs.voucher_id = v.id)))")
    java.util.List<Long> selectPurchasedShopIds(@Param("userId") Long userId);
}
