package com.momentliving.controller;

import com.momentliving.dto.AdminLoginDTO;
import com.momentliving.result.Result;
import com.momentliving.service.AdminService;
import com.momentliving.vo.AdminVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@Slf4j
public class AdminController {

    @Resource
    private AdminService adminService;

    /**
     * 管理员登录
     */
    @PostMapping("/login")
    public Result<AdminVO> login(@RequestBody AdminLoginDTO loginDTO) {
        log.info("管理员登录：{}", loginDTO.getUsername());
        return adminService.login(loginDTO);
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        adminService.logout();
        return Result.success();
    }

    /**
     * 当前登录管理员信息
     */
    @GetMapping("/me")
    public Result<AdminVO> me() {
        return adminService.me();
    }
}
