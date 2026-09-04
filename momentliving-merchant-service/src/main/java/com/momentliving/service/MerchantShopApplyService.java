package com.momentliving.service;

import com.momentliving.dto.ShopApplyDTO;
import com.momentliving.entity.ShopApply;
import com.momentliving.result.Result;

import java.util.List;

/**
 * 商家端开店申请：提交 + 查我自己的申请（审核在 admin-service）
 */
public interface MerchantShopApplyService {

    /** 提交开店申请（同一商家存在待审核申请时不允许重复提交） */
    Result<Long> submit(Long merchantId, ShopApplyDTO dto);

    /** 我的开店申请（按提交时间倒序） */
    Result<List<ShopApply>> listMine(Long merchantId);
}
