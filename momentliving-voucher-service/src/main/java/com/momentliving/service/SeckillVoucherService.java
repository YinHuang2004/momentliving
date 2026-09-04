package com.momentliving.service;

import com.momentliving.entity.SeckillVoucher;

public interface SeckillVoucherService {

    void save(SeckillVoucher seckillVoucher);

    SeckillVoucher getById(Long voucherId);

    void update(SeckillVoucher seckillVoucher);

    /**
     * 按 voucherId 删除秒杀信息（删除优惠券时联动）
     */
    void deleteByVoucherId(Long voucherId);

    int deductStock(Long voucherId);
}
