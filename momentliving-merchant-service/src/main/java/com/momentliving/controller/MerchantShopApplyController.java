package com.momentliving.controller;

import com.momentliving.context.MerchantHolder;
import com.momentliving.dto.ShopApplyDTO;
import com.momentliving.entity.ShopApply;
import com.momentliving.exception.BadRequestException;
import com.momentliving.result.Result;
import com.momentliving.service.MerchantShopApplyService;
import com.momentliving.vo.MerchantVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 开店申请（商家端，/merchant/** 由 MerchantAuthInterceptor 鉴权，需商家登录）
 *
 * <p>规则：管理员不能直接新增店铺——店铺上线只有"商家提交申请 → 平台审核通过"一条路。
 * 提交的申请在 admin-service 审核接口里经 Seata 全局事务建店。
 */
@RestController
@RequestMapping("/merchant/shop")
public class MerchantShopApplyController {

    @Resource
    private MerchantShopApplyService merchantShopApplyService;

    /** 提交开店申请：{shopName, typeId, address, contactPhone} */
    @PostMapping("/apply")
    public Result<Long> apply(@RequestBody ShopApplyDTO dto) {
        return merchantShopApplyService.submit(currentMerchantId(), dto);
    }

    /** 我的开店申请列表（最新在前，含审核状态/拒绝原因/店铺ID） */
    @GetMapping("/apply/list")
    public Result<List<ShopApply>> listMine() {
        return merchantShopApplyService.listMine(currentMerchantId());
    }

    private Long currentMerchantId() {
        MerchantVO merchant = MerchantHolder.getMerchant();
        if (merchant == null) {
            throw new BadRequestException("未登录，请先登录商家端");
        }
        return merchant.getId();
    }
}
