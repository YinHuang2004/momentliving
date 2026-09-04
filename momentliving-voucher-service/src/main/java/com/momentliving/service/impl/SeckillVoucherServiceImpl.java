package com.momentliving.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.momentliving.entity.SeckillVoucher;
import com.momentliving.mapper.SeckillVoucherMapper;
import com.momentliving.service.SeckillVoucherService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class SeckillVoucherServiceImpl implements SeckillVoucherService {

    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;

    @Override
    public void save(SeckillVoucher seckillVoucher) {
        seckillVoucherMapper.insert(seckillVoucher);
    }

    @Override
    public SeckillVoucher getById(Long voucherId) {
        return seckillVoucherMapper.selectById(voucherId);
    }

    @Override
    public void update(SeckillVoucher seckillVoucher) {
        seckillVoucherMapper.updateById(seckillVoucher);
    }

    @Override
    public void deleteByVoucherId(Long voucherId) {
        LambdaQueryWrapper<SeckillVoucher> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SeckillVoucher::getVoucherId, voucherId);
        seckillVoucherMapper.delete(wrapper);
    }

    @Override
    public int deductStock(Long voucherId) {
        return seckillVoucherMapper.deductStock(voucherId);
    }

}
