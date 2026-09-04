package com.momentliving.controller;

import com.momentliving.dto.MerchantApplyDTO;
import com.momentliving.dto.MerchantLoginDTO;
import com.momentliving.dto.MerchantUpdateDTO;
import com.momentliving.result.Result;
import com.momentliving.service.MerchantApplyService;
import com.momentliving.service.MerchantService;
import com.momentliving.vo.MerchantVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 商家端接口（merchant 表账号体系，与平台管理员 /admin/** 分离）
 * - POST /merchant/login  登录（公开）
 * - POST /merchant/logout 退出（需登录）
 * - GET  /merchant/me     当前商家信息（需登录）
 * - PUT  /merchant/me     修改个人信息：姓名/手机号/头像/密码（需登录）
 * - POST /merchant/apply  入驻申请（公开，审核通过后生成 merchant 账号）
 */
@RestController
@RequestMapping("/merchant")
@Slf4j
public class MerchantController {

    @Resource
    private MerchantService merchantService;
    @Resource
    private MerchantApplyService merchantApplyService;

    /** 商家登录（商家账号在独立 merchant 表，admin 登录不再接受商家） */
    @PostMapping("/login")
    public Result<MerchantVO> login(@RequestBody MerchantLoginDTO dto) {
        log.info("商家登录：{}", dto.getUsername());
        return merchantService.login(dto);
    }

    /** 商家退出登录 */
    @PostMapping("/logout")
    public Result<Void> logout() {
        merchantService.logout();
        return Result.success();
    }

    /** 当前登录商家信息（含 shopId，"我的店铺"依赖） */
    @GetMapping("/me")
    public Result<MerchantVO> me() {
        return merchantService.me();
    }

    /** 修改当前商家个人信息（姓名/手机号/头像，可选改密），返回更新后的信息 */
    @PutMapping("/me")
    public Result<MerchantVO> updateMe(@RequestBody MerchantUpdateDTO dto) {
        return merchantService.updateMe(dto);
    }

    /** 商家入驻申请（公开：MvcConfig 放行，审核通过后生成 merchant 账号） */
    @PostMapping("/apply")
    public Result<Long> apply(@RequestBody MerchantApplyDTO dto) {
        return merchantApplyService.apply(dto);
    }
}
