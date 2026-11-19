package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.Merchant;
import org.apache.ibatis.annotations.Mapper;

/** 商家账号表 Mapper（商家登录/个人信息读写、工作台查绑定店铺） */
@Mapper
public interface MerchantMapper extends BaseMapper<Merchant> {
}
