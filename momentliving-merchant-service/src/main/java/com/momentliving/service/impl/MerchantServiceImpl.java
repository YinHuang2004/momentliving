package com.momentliving.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.momentliving.constant.RedisConstants;
import com.momentliving.context.MerchantHolder;
import com.momentliving.dto.MerchantLoginDTO;
import com.momentliving.dto.MerchantUpdateDTO;
import com.momentliving.entity.Merchant;
import com.momentliving.exception.BadRequestException;
import com.momentliving.mapper.MerchantMapper;
import com.momentliving.properties.JwtProperties;
import com.momentliving.result.Result;
import com.momentliving.service.MerchantService;
import com.momentliving.utils.JwtUtils;
import com.momentliving.vo.MerchantVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 商家账号服务（merchant 表，区别于平台管理员 AdminServiceImpl）
 * 登录态：Redis login:merchant:{id}（与 login:admin:{id} 天然隔离，JWT 同 secret 无妨）
 */
@Service
@Slf4j
public class MerchantServiceImpl implements MerchantService {

    @Resource
    private MerchantMapper merchantMapper;
    @Resource
    private JwtProperties jwtProperties;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Result<MerchantVO> login(MerchantLoginDTO dto) {
        if (dto == null || StrUtil.hasBlank(dto.getUsername(), dto.getPassword())) {
            throw new BadRequestException("用户名和密码不能为空");
        }
        // 1. 按用户名查商家账号（merchant 表，不再查 admin）
        Merchant merchant = merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getUsername, dto.getUsername()));
        if (merchant == null) {
            throw new BadRequestException("用户名或密码错误");
        }
        // 2. BCrypt 校验密码
        if (!passwordEncoder.matches(dto.getPassword(), merchant.getPassword())) {
            throw new BadRequestException("用户名或密码错误");
        }
        // 3. 校验账号状态
        if (merchant.getStatus() == null || merchant.getStatus() != 1) {
            throw new BadRequestException("账号已被禁用，请联系平台");
        }
        // 4. 签发商家端 AccessToken
        String token = JwtUtils.createAccessToken(
                merchant.getId(), jwtProperties.getAccessTokenTtl(), jwtProperties.getSecret());
        // 5. 登录态存 Redis（商家端专用 key：login:merchant:xxx）
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_MERCHANT_KEY + merchant.getId(),
                token,
                Duration.ofMinutes(jwtProperties.getAccessTokenTtl())
        );
        // 6. 返回脱敏信息
        MerchantVO vo = BeanUtil.copyProperties(merchant, MerchantVO.class);
        vo.setToken(token);
        return Result.success(vo);
    }

    @Override
    public void logout() {
        MerchantVO merchant = MerchantHolder.getMerchant();
        if (merchant != null) {
            stringRedisTemplate.delete(RedisConstants.LOGIN_MERCHANT_KEY + merchant.getId());
        }
    }

    @Override
    public Result<MerchantVO> me() {
        MerchantVO holder = MerchantHolder.getMerchant();
        if (holder == null) {
            throw new BadRequestException("未登录");
        }
        // 拦截器只塞了 id，这里回查 DB 补全（username/name/phone/shopId 等，商家端"我的店铺"依赖 shopId）
        Merchant merchant = merchantMapper.selectById(holder.getId());
        if (merchant == null) {
            throw new BadRequestException("账号不存在");
        }
        MerchantVO vo = BeanUtil.copyProperties(merchant, MerchantVO.class);
        vo.setToken(null);   // token 不回显（登录时才签发）
        return Result.success(vo);
    }

    @Override
    public Result<MerchantVO> updateMe(MerchantUpdateDTO dto) {
        MerchantVO holder = MerchantHolder.getMerchant();
        if (holder == null) {
            throw new BadRequestException("未登录");
        }
        Merchant merchant = merchantMapper.selectById(holder.getId());
        if (merchant == null) {
            throw new BadRequestException("账号不存在");
        }
        // 只更新入参提供的字段：MP updateById 默认忽略 null，用局部实体避免覆盖未传字段
        Merchant update = new Merchant();
        update.setId(merchant.getId());
        if (dto.getName() != null) {
            String name = dto.getName().trim();
            if (StrUtil.isBlank(name)) {
                throw new BadRequestException("姓名不能为空");
            }
            update.setName(name);
        }
        if (dto.getPhone() != null) {
            String phone = dto.getPhone().trim();
            if (StrUtil.isBlank(phone)) {
                throw new BadRequestException("手机号不能为空");
            }
            if (!phone.matches("^1[3-9]\\d{9}$")) {
                throw new BadRequestException("手机号格式不正确");
            }
            update.setPhone(phone);
        }
        if (dto.getAvatar() != null) {
            update.setAvatar(dto.getAvatar().trim());
        }
        // 改密：oldPassword/newPassword 同时提供才触发，改后登录态保留（token 未变）
        if (StrUtil.isNotBlank(dto.getNewPassword())) {
            if (StrUtil.isBlank(dto.getOldPassword())) {
                throw new BadRequestException("请输入旧密码");
            }
            if (!passwordEncoder.matches(dto.getOldPassword(), merchant.getPassword())) {
                throw new BadRequestException("旧密码错误");
            }
            if (dto.getNewPassword().length() < 6) {
                throw new BadRequestException("新密码至少 6 位");
            }
            update.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        }
        if (update.getName() == null && update.getPhone() == null
                && update.getAvatar() == null && update.getPassword() == null) {
            throw new BadRequestException("没有需要修改的内容");
        }
        merchantMapper.updateById(update);
        // 回查返回最新信息，前端据此同步本地缓存
        Merchant latest = merchantMapper.selectById(merchant.getId());
        MerchantVO vo = BeanUtil.copyProperties(latest, MerchantVO.class);
        vo.setToken(null);
        return Result.success(vo);
    }
}
