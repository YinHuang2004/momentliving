package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.SeckillVoucher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/** 秒杀券库存 Mapper：DB 侧条件扣减与回补（Redis 为预扣层，DB 为真源） */
@Mapper
public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {
    /** 条件扣库存：stock > 0 才减 1，rows=0 即售罄（防超卖） */
    @Update("update seckill_voucher set stock = stock - 1 where voucher_id = #{voucherId} and stock > 0")
    int deductStock(Long voucherId);

    /** 超时关单时回补库存：下单消费时 DB 扣了 1，关单要加回来 */
    @Update("update seckill_voucher set stock = stock + 1 where voucher_id = #{voucherId}")
    int restoreStock(Long voucherId);
}
