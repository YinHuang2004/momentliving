package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.Merchant;
import org.apache.ibatis.annotations.Mapper;

/** 商家账号表 Mapper（入驻审核通过后写入商家账号 + 账号占用校验） */
@Mapper
public interface MerchantMapper extends BaseMapper<Merchant> {
}
