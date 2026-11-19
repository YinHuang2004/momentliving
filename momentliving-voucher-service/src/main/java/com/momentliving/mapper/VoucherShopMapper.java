package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.VoucherShop;
import org.apache.ibatis.annotations.Mapper;

/** 券-店铺适用范围 Mapper（多对多关系表：无记录 = 全场通用） */
@Mapper
public interface VoucherShopMapper extends BaseMapper<VoucherShop> {
}
