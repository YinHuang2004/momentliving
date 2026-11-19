package com.momentliving.service;

import com.momentliving.entity.SeckillVoucher;

/** 秒杀券信息服务：秒杀信息增删改查与 DB 条件扣库存 */
public interface SeckillVoucherService {

    void save(SeckillVoucher seckillVoucher);

    SeckillVoucher getById(Long voucherId);

    void update(SeckillVoucher seckillVoucher);

    /**
     * 按 voucherId 删除秒杀信息（删除优惠券时联动）
     */
    void deleteByVoucherId(Long voucherId);

    /** 条件扣库存（stock>0 才减），返回受影响行数，0 = 售罄 */
    int deductStock(Long voucherId);
}
