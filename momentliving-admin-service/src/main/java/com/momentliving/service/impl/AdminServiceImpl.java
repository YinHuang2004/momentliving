package com.momentliving.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.momentliving.constant.MessageConstant;
import com.momentliving.constant.RedisConstants;
import com.momentliving.context.AdminHolder;
import com.momentliving.dto.AdminLoginDTO;
import com.momentliving.entity.Admin;
import com.momentliving.exception.BadRequestException;
import com.momentliving.mapper.AdminMapper;
import com.momentliving.properties.JwtProperties;
import com.momentliving.result.Result;
import com.momentliving.service.AdminService;
import com.momentliving.utils.JwtUtils;
import com.momentliving.vo.AdminVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
public class AdminServiceImpl implements AdminService {

    @Resource
    private AdminMapper adminMapper;
    @Resource
    private JwtProperties jwtProperties;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Result<AdminVO> login(AdminLoginDTO loginDTO) {
        // 1. 按用户名查管理员
        Admin admin = adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getUsername, loginDTO.getUsername()));
        if (admin == null) {
            throw new BadRequestException("用户名或密码错误");
        }
        // 2. BCrypt 校验密码
        if (!passwordEncoder.matches(loginDTO.getPassword(), admin.getPassword())) {
            throw new BadRequestException("用户名或密码错误");
        }
        // 3. 校验账号状态
        if (admin.getStatus() == null || admin.getStatus() != 1) {
            throw new BadRequestException("账号已被禁用，请联系平台");
        }
        // 注：admin 表只存平台管理员（商家账号在 merchant 表，走 /merchant/login），
        //     无需再按 role 区分；前端多传的 role 字段会被 Jackson 忽略
        // 4. 签发管理端 AccessToken（key 用管理员 id + 前缀，与用户端隔离）
        String token = JwtUtils.createAccessToken(
                admin.getId(), jwtProperties.getAccessTokenTtl(), jwtProperties.getSecret());
        // 5. 登录态存 Redis（管理端专用 key：login:admin:xxx）
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_ADMIN_KEY + admin.getId(),
                token,
                Duration.ofMinutes(jwtProperties.getAccessTokenTtl())
        );
        // 6. 返回脱敏信息
        AdminVO adminVO = BeanUtil.copyProperties(admin, AdminVO.class);
        adminVO.setImages(admin.getImages());
        adminVO.setToken(token);
        return Result.success(adminVO);
    }

    @Override
    public void logout() {
        AdminVO admin = AdminHolder.getAdmin();
        if (admin != null) {
            stringRedisTemplate.delete(RedisConstants.LOGIN_ADMIN_KEY + admin.getId());
        }
    }

    @Override
    public Result<AdminVO> me() {
        AdminVO holder = AdminHolder.getAdmin();
        if (holder == null) {
            throw new BadRequestException("未登录");
        }
        // 拦截器只塞了 id，这里回查 DB 补全（username/name/phone/shopId 等，商家端"我的店铺"依赖 shopId）
        Admin admin = adminMapper.selectById(holder.getId());
        if (admin == null) {
            throw new BadRequestException("账号不存在");
        }
        AdminVO vo = BeanUtil.copyProperties(admin, AdminVO.class);
        vo.setToken(null);   // token 不回显（登录时才签发）
        return Result.success(vo);
    }
}
