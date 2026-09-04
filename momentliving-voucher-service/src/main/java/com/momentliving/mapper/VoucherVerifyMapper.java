package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.VoucherVerify;
import com.momentliving.vo.RecentVerifyVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 核销记录 Mapper（uk_verify_code 全局唯一）
 * 状态：0未核销 1已核销 2已作废
 */
@Mapper
public interface VoucherVerifyMapper extends BaseMapper<VoucherVerify> {

    /** 商家核销：未核销(0) → 已核销(1)，记录核销人与时间。rows=0 即已被并发核销 */
    @Update("update voucher_verify set status = 1, verify_by = #{merchantId}, verify_time = now() " +
            "where id = #{id} and status = 0")
    int casVerify(@Param("id") Long id, @Param("merchantId") Long merchantId);

    /** 商家端统计：某店铺今日（verify_time >= start）核销成功数 */
    @Select("select count(*) from voucher_verify " +
            "where shop_id = #{shopId} and status = 1 and verify_time >= #{start}")
    long countVerifiedSince(@Param("shopId") Long shopId, @Param("start") LocalDateTime start);

    /** 商家端统计：某店铺今日核销营收（分，JOIN 订单→券表取 payValue 求和） */
    @Select("select coalesce(sum(v.pay_value), 0) from voucher_verify vv " +
            "join voucher_order o on vv.order_id = o.id " +
            "join voucher v on o.voucher_id = v.id " +
            "where vv.shop_id = #{shopId} and vv.status = 1 and vv.verify_time >= #{start}")
    long sumRevenueFenSince(@Param("shopId") Long shopId, @Param("start") LocalDateTime start);

    /** 商家端统计：某店铺最近核销（按核销时间倒序，JOIN 订单/券表补券名与码尾号） */
    @Select("select vv.order_id as orderId, v.title as voucherTitle, vv.user_id as userId, " +
            "vv.verify_time as verifyTime, vv.status as status, right(vv.verify_code, 4) as verifyCodeTail " +
            "from voucher_verify vv " +
            "join voucher_order o on vv.order_id = o.id " +
            "join voucher v on o.voucher_id = v.id " +
            "where vv.shop_id = #{shopId} and vv.status = 1 " +
            "order by vv.verify_time desc limit #{limit}")
    List<RecentVerifyVO> selectRecentVerified(@Param("shopId") Long shopId, @Param("limit") int limit);
}
