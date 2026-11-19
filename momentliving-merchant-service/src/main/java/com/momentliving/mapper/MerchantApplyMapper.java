package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.MerchantApply;

/** 商家入驻申请表 Mapper（商家提交/复用申请落库，审核在 admin-service） */
public interface MerchantApplyMapper extends BaseMapper<MerchantApply> {
}
