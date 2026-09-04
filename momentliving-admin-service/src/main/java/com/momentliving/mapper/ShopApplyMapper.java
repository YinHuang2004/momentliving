package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.ShopApply;

/**
 * 开店申请表 Mapper（admin-service 审核读写；申请由 merchant-service 提交，两服务共库）
 */
public interface ShopApplyMapper extends BaseMapper<ShopApply> {
}
