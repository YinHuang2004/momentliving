package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.ShopFavorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 店铺收藏 Mapper（shop_favorite 表，user_id + shop_id 唯一）
 */
@Mapper
public interface ShopFavoriteMapper extends BaseMapper<ShopFavorite> {
}
