package com.momentliving.service;

import com.momentliving.entity.ShopType;

import java.util.List;

public interface ShopTypeService {

    /**
     * 查询所有商铺类型（按 sort 升序）
     */
    List<ShopType> queryList();

    /**
     * 根据 id 查询商铺类型
     */
    ShopType getById(Long id);

    /**
     * 新增商铺类型
     */
    Long save(ShopType shopType);

    /**
     * 更新商铺类型
     */
    void update(ShopType shopType);

    /**
     * 删除商铺类型
     */
    void delete(Long id);
}
