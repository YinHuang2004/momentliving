package com.momentliving.controller;

import com.momentliving.dto.MerchantApplyAuditDTO;
import com.momentliving.result.Result;
import com.momentliving.service.MerchantApplyService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.momentliving.entity.MerchantApply;

/**
 * 商家入驻审核接口（平台管理员专属，/admin/**）
 *
 * <p>POST /merchant/apply —— 入驻申请已迁至 MerchantController（公开，商家端提交）；
 * GET  /admin/apply/list      —— 平台管理员查看申请（管理后台/接口调用）；
 * POST /admin/apply/audit     —— 平台管理员审核，通过则事务内建店 + 写入 merchant 表（商家账号）。
 */
@RestController
public class MerchantApplyController {

    @Resource
    private MerchantApplyService merchantApplyService;

    /** 申请列表（仅平台管理员）：status 0待审核 1已通过 2已拒绝，不传查全部 */
    @GetMapping("/admin/apply/list")
    public Result<List<MerchantApply>> list(@RequestParam(value = "status", required = false) Integer status,
                                            @RequestParam(value = "current", required = false) Integer current,
                                            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return merchantApplyService.list(status, current, pageSize);
    }

    /** 审核（仅平台管理员）：{id, approved, reason} */
    @PostMapping("/admin/apply/audit")
    public Result<Void> audit(@RequestBody MerchantApplyAuditDTO dto) {
        return merchantApplyService.audit(dto);
    }
}
