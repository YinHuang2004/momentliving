package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.momentliving.entity.Shop;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 店铺 Mapper（selectShopByIds 为 XML 实现，见 resources/mapper/ShopMapper.xml）
 */
@Mapper
public interface ShopMapper extends BaseMapper<Shop> {




    // 默认方法：获取所有店铺 ID 列表
    default List<Long> selectAllIds() {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Shop::getId);  // ✅ 方法引用
        return selectList(wrapper).stream()
                .map(Shop::getId)
                .collect(Collectors.toList());
    }

    /** 批量查店铺并按传入 id 顺序返回（order by field），GEO 按距离排序的结果回表时保持次序 */
    List<Shop> selectShopByIds(List<Long> ids);
}
