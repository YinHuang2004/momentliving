package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.ShopApply;

/**
 * 开店申请表 Mapper（merchant-service 写入申请，admin-service 审核读写）
 */
public interface ShopApplyMapper extends BaseMapper<ShopApply> {
}
