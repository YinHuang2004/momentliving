package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.SeckillVoucher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {
    @Update("update seckill_voucher set stock = stock - 1 where voucher_id = #{voucherId} and stock > 0")
    int deductStock(Long voucherId);

    /** 超时关单时回补库存：下单消费时 DB 扣了 1，关单要加回来 */
    @Update("update seckill_voucher set stock = stock + 1 where voucher_id = #{voucherId}")
    int restoreStock(Long voucherId);
}
