package com.momentliving.controller;

import com.momentliving.dto.ShopApplyAuditDTO;
import com.momentliving.entity.ShopApply;
import com.momentliving.result.Result;
import com.momentliving.service.ShopApplyAuditService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 开店申请审核接口（平台管理员专属，/admin/** 由 AdminAuthInterceptor 鉴权）
 *
 * <p>规则：管理员不能直接新增店铺。店铺上线 = 商家在 merchant-service 提交开店申请
 * （POST /merchant/shop/apply）→ 这里审核通过（Seata 全局事务建店）。
 */
@RestController
@RequestMapping("/admin/shop")
public class ShopApplyController {

    @Resource
    private ShopApplyAuditService shopApplyAuditService;

    /** 开店申请列表：status 0待审核 1已通过 2已拒绝，不传查全部 */
    @GetMapping("/apply/list")
    public Result<List<ShopApply>> list(@RequestParam(value = "status", required = false) Integer status,
                                        @RequestParam(value = "current", required = false) Integer current,
                                        @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return shopApplyAuditService.list(status, current, pageSize);
    }

    /** 审核：{id, approved, reason(拒绝必填), drillFail(演练可选)} */
    @PostMapping("/apply/audit")
    public Result<Void> audit(@RequestBody ShopApplyAuditDTO dto) {
        return shopApplyAuditService.audit(dto);
    }
}
