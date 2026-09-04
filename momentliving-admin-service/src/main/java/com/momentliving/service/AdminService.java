package com.momentliving.service;

import com.momentliving.dto.AdminLoginDTO;
import com.momentliving.result.Result;
import com.momentliving.vo.AdminVO;

public interface AdminService {

    /**
     * 管理员登录（BCrypt 校验 + 签发管理端 JWT）
     */
    Result<AdminVO> login(AdminLoginDTO loginDTO);

    /**
     * 退出登录（删除 Redis 登录态）
     */
    void logout();

    /**
     * 获取当前登录管理员信息
     */
    Result<AdminVO> me();
}
