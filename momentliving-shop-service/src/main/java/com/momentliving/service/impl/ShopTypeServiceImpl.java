package com.momentliving.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.momentliving.constant.RedisConstants;
import com.momentliving.entity.ShopType;
import com.momentliving.mapper.ShopTypeMapper;
import com.momentliving.service.ShopTypeService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShopTypeServiceImpl implements ShopTypeService {

    @Resource
    private ShopTypeMapper shopTypeMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> queryList() {
        LambdaQueryWrapper<ShopType> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(ShopType::getSort);
        return shopTypeMapper.selectList(wrapper);
    }

    @Override
    public ShopType getById(Long id) {
        return shopTypeMapper.selectById(id);
    }

    @Override
    public Long save(ShopType shopType) {
        //添加缓存
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE_KEY, shopType.getId().toString());
        shopTypeMapper.insert(shopType);
        return shopType.getId();
    }

    @Override
    public void update(ShopType shopType) {
        shopTypeMapper.updateById(shopType);
    }

    @Override
    public void delete(Long id) {
        shopTypeMapper.deleteById(id);
    }
}
