package com.momentliving.service;

import com.momentliving.dto.AdminLoginDTO;
import com.momentliving.result.Result;
import com.momentliving.vo.AdminVO;

/**
 * 管理员登录态服务：登录（BCrypt 校验 + 签发 JWT + Redis 登录态）、退出、当前信息
 */
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
