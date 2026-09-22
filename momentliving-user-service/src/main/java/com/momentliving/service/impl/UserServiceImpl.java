package com.momentliving.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.momentliving.constant.MessageConstant;
import com.momentliving.constant.RedisConstants;
import com.momentliving.constant.SystemConstants;
import com.momentliving.context.UserHolder;
import com.momentliving.dto.LoginFormDTO;
import com.momentliving.dto.PasswordResetDTO;
import com.momentliving.dto.PasswordUpdateDTO;
import com.momentliving.entity.User;
import com.momentliving.entity.UserInfo;
import com.momentliving.exception.BadRequestException;
import com.momentliving.exception.VerificationCodeException;
import com.momentliving.mapper.UserMapper;
import com.momentliving.properties.JwtProperties;
import com.momentliving.result.Result;
import com.momentliving.service.UserInfoService;
import com.momentliving.service.UserService;
import com.momentliving.utils.JwtUtils;
import com.momentliving.utils.RegexUtils;
import com.momentliving.vo.CaptchaVO;
import com.momentliving.vo.CreditsVO;
import com.momentliving.vo.FootprintSettingVO;
import com.momentliving.vo.LoginVO;
import com.momentliving.vo.UserVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserMapper userMapper;
    @Resource
    private JwtProperties jwtProperties;
    @Resource
    private UserInfoService userInfoService;
    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String mailFrom;   // 发件人

    /**
     * BCrypt 密码编码器（与 merchant/admin 端同一套加密约定）。
     * 只引入 spring-security-crypto 单包，不含 Security 完整 starter，无自动登录页等副作用。
     */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 发送验证码
     */
    @Override
    public void sendCode(String email) {
        // 1. 校验邮箱格式
        if (RegexUtils.isEmailInvalid(email)) {
            throw new BadRequestException("邮箱格式错误");
        }
        String intervalKey = RedisConstants.LOGIN_CODE_INTERVAL_KEY + email;
        Boolean isSuccess = stringRedisTemplate.opsForValue().setIfAbsent(intervalKey, "1", RedisConstants.LOGIN_CODE_INTERVAL_TTL, TimeUnit.SECONDS);
        if (!isSuccess) {
            throw new BadRequestException("验证码发送间隔过短，请稍后再试");
        }
        // 2. 生成 6 位验证码
        String code = RandomUtil.randomNumbers(6);
        // 3. 存 Redis（key 用邮箱，2 分钟有效）
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_CODE_KEY + email,
                code,
                RedisConstants.LOGIN_CODE_TTL,
                TimeUnit.MINUTES
        );
        // 4. 真正发送到 QQ 邮箱
        sendEmailCode(email, code);
    }
    /**
     * 发送邮件验证码（QQ SMTP）
     */
    private void sendEmailCode(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("【一刻生活】登录验证码");
        message.setText("您的登录验证码是：" + code + "，2 分钟内有效。如非本人操作请忽略。");
        mailSender.send(message);
        log.info("验证码已发送至邮箱：{}", email);
    }

    /**
     * 发送手机号验证码（演示模式：未接短信服务商，验证码直接返回前端展示）。
     * 过期由 Redis TTL 保证（LOGIN_CODE_TTL 分钟），前端按 expireSeconds 做过期倒计时。
     */
    @Override
    public CaptchaVO sendPhoneCode(String phone) {
        // 1. 校验手机号格式
        if (RegexUtils.isPhoneInvalid(phone)) {
            throw new BadRequestException("手机号格式错误");
        }
        // 2. 60s 发送间隔防刷（与邮箱验证码共用 interval key 规则）
        String intervalKey = RedisConstants.LOGIN_CODE_INTERVAL_KEY + phone;
        Boolean isSuccess = stringRedisTemplate.opsForValue().setIfAbsent(
                intervalKey, "1", RedisConstants.LOGIN_CODE_INTERVAL_TTL, TimeUnit.SECONDS);
        if (!isSuccess) {
            throw new BadRequestException("验证码发送间隔过短，请稍后再试");
        }
        // 3. 生成 6 位验证码，存 Redis（key 用手机号，LOGIN_CODE_TTL 分钟有效）
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_CODE_KEY + phone,
                code,
                RedisConstants.LOGIN_CODE_TTL,
                TimeUnit.MINUTES
        );
        // 4. 演示模式：直接返回前端展示，同时打日志留痕
        log.info("手机号验证码（演示模式，直接返回前端）：{} -> {}", phone, code);
        return CaptchaVO.builder()
                .code(code)
                .expireSeconds(RedisConstants.LOGIN_CODE_TTL * 60)
                .build();
    }

    /**
     * 登录功能（phone 与 email 二选一，phone 优先）
     */
    public Result<LoginVO> loginByEmail(LoginFormDTO loginForm) {
        if (StrUtil.isNotBlank(loginForm.getPhone())) {
            return loginByPhone(loginForm);
        }
        String email = loginForm.getEmail();
        String code = loginForm.getCode();

        if (RegexUtils.isEmailInvalid(email)) {
            throw new BadRequestException(MessageConstant.EMAIL_FORMAT_ERROR);
        }

        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + email);
        if (cacheCode == null) {
            throw new VerificationCodeException(MessageConstant.VERIFICATION_CODE_EXPIRED);
        }

        if (!cacheCode.equals(code)) {
            throw new VerificationCodeException(MessageConstant.VERIFICATION_CODE_ERROR);
        }
        //比较完立马删除验证码(防止用户在验证码有效期重复调用login）
        stringRedisTemplate.delete(RedisConstants.LOGIN_CODE_KEY + email);
        //从数据库根据号码查询用户信息

        User user= userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if(user==null){
            user = addUserWithEmail(email);
        }
        return buildLoginVO(user);

    }

    /**
     * 手机号验证码登录：校验 Redis 验证码（过期/错误分别提示），用后即删，手机号不存在则注册新用户
     */
    private Result<LoginVO> loginByPhone(LoginFormDTO loginForm) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            throw new BadRequestException("手机号格式错误");
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if (cacheCode == null) {
            // Redis key 到期自动删除 = 验证码已过有效期
            throw new VerificationCodeException(MessageConstant.VERIFICATION_CODE_EXPIRED);
        }
        if (!cacheCode.equals(loginForm.getCode())) {
            throw new VerificationCodeException(MessageConstant.VERIFICATION_CODE_ERROR);
        }
        stringRedisTemplate.delete(RedisConstants.LOGIN_CODE_KEY + phone);
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null) {
            user = addUserWithPhone(phone);
        }
        return buildLoginVO(user);
    }
    private Result<LoginVO> buildLoginVO(User user){
        //为什么需要双token，单token痛点在哪
        //token有效期短，安全，但用户半小时就要重新登录，体验差
        //Token 有效期长（比如 7 天）→ 方便，但一旦泄露，攻击者能用 7 天



        //工作流程
        //
        //  用户登录
        //    → 返回 AccessToken (30分钟) + RefreshToken (7天，存 Redis)
        //        ↓
        //  用户请求 /shop/xxx，带 AccessToken
        //    → 拦截器解析 AccessToken，拿到 userId
        //    → 同时去 Redis 用 RefreshToken 续一下过期时间
        //    → 放行
        //        ↓
        //  30 分钟后，AccessToken 过期
        //    → 前端用 RefreshToken 调 /user/refresh
        //    → 后端校验 Redis 里的 RefreshToken 还有效
        //    → 签发新的 AccessToken

        //开启主键id开关，根据主键id生成jwt
        String accessToken = JwtUtils.createAccessToken(user.getId(),jwtProperties.getAccessTokenTtl(),jwtProperties.getSecret());
        String refreshToken = JwtUtils.createRefreshToken(user.getId(),jwtProperties.getRefreshTokenTtl(),jwtProperties.getSecret());
        //将refreshtoken存入redis中
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_USER_KEY + user.getId(),
                refreshToken,
                Duration.ofDays(7)
        );
        UserVO userVO=new UserVO();
        BeanUtils.copyProperties(user,userVO);
        LoginVO loginVO=LoginVO.builder().token(accessToken).refreshToken(refreshToken).userInfo(userVO).build();
        return Result.success(loginVO);
    }
    @Override
    public Result<Map<String, String>> refreshToken(String refreshToken) {
        // 1. 解析 RefreshToken 获取 userId
        Long userId;
        try {
            userId = JwtUtils.getUserId(refreshToken,jwtProperties.getSecret());
        } catch (Exception e) {
            throw new BadRequestException("RefreshToken 无效或已过期，请重新登录");
        }

        // 2. 查 Redis，校验 RefreshToken 是否还存在（可通过删除 Redis 来主动失效）
        String storedToken = stringRedisTemplate.opsForValue()
                .get(RedisConstants.LOGIN_USER_KEY + userId);
        if (storedToken == null || !storedToken.equals(refreshToken)) {
            throw new BadRequestException("RefreshToken 已失效，请重新登录");
        }

        // 3. 签发新的 AccessToken+RefreshToken（令牌轮换
        String newAccessToken = JwtUtils.createAccessToken(userId,jwtProperties.getAccessTokenTtl(),jwtProperties.getSecret());
        String newRefreshToken=JwtUtils.createRefreshToken(userId,jwtProperties.getRefreshTokenTtl(),jwtProperties.getSecret());
        //覆盖旧refreshToken
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_USER_KEY + userId,
                newRefreshToken,
                Duration.ofDays(7)
        );

        // 4. 返回
        return Result.success(Map.of("token", newAccessToken,"refreshToken",newRefreshToken));
    }

    /**
     * 用户退出，主动失效token
     */
    @Override
    public void logout() {
        //获取当前用户id
        UserVO user = UserHolder.getUser();
        stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY+user.getId());

    }

    // ========== 密码管理（修改 / 找回） ==========

    /**
     * 修改密码（已登录）。
     * <p>安全要点：成功后必须删除 Redis 的 RefreshToken。
     * RefreshToken 是"短命 AccessToken 的 7 天续命券"，改密码往往意味着用户怀疑账号不安全
     * （或刚在他人设备上登录过），此时攻击者手里可能还捏着旧 RefreshToken——
     * 不吊销的话，他仍能在接下来 7 天内无限调 /user/refresh 换新 AccessToken，改密码形同虚设。
     * <p>本项目的网关对每个请求都会回查 login:refresh:{userId} 是否存在（不存在直接 401），
     * 所以删掉这一个 key 即可同时废掉所有已签发的 AccessToken，全端立即下线。
     */
    @Override
    public void updatePassword(PasswordUpdateDTO dto) {
        Long userId = UserHolder.getUser().getId();
        String newPassword = dto.getNewPassword();
        // 1. 新密码基础校验（6~32 位，防止空串/超长打爆 BCrypt 与 DB 列）
        if (StrUtil.isBlank(newPassword) || newPassword.length() < 6 || newPassword.length() > 32) {
            throw new BadRequestException("新密码长度需在 6~32 位之间");
        }
        // 2. 查当前用户，判断是否已设置过密码
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BadRequestException("用户不存在");
        }
        if (StrUtil.isNotBlank(user.getPassword())) {
            // 2.1 已设置过密码：必须提供原密码且校验通过（防止设备被借用时被改密码）
            if (StrUtil.isBlank(dto.getOldPassword())) {
                throw new BadRequestException("请输入原密码");
            }
            if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
                throw new BadRequestException("原密码错误");
            }
        }
        // 2.2 未设置过密码（纯验证码注册）：本次即"首次设置密码"，无需原密码
        // 3. 更新密码：只 set id+password 两列，避免 updateById 意外覆盖其他字段
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(update);
        // 4. ★ 吊销登录态：删除 Redis RefreshToken，全端（含当前设备）立即下线，
        //    前端收到本响应后应清空本地 token 并跳转登录页
        stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + userId);
        log.info("用户:{} 修改密码成功，已吊销全部登录态", userId);
    }

    /**
     * 找回密码（无需登录）：邮箱验证码校验通过后重置密码。
     * <p>这是账号安全里最敏感的动作——用户走找回流程通常是因为"密码可能已被他人掌握"，
     * 所以重置成功后必须吊销该账号全部登录态，否则盗号者手里的旧 RefreshToken 依然有效。
     * <p>时序注意：吊销放在"验证码校验通过、真正重置"这一刻，而不是"发验证码"时，
     * 否则用户输错验证码就会把自己踢下线。
     */
    @Override
    public void resetPassword(PasswordResetDTO dto) {
        String email = dto.getEmail();
        // 1. 参数基础校验
        if (RegexUtils.isEmailInvalid(email)) {
            throw new BadRequestException("邮箱格式错误");
        }
        if (StrUtil.isBlank(dto.getCode())) {
            throw new BadRequestException("请输入邮箱验证码");
        }
        if (StrUtil.isBlank(dto.getNewPassword()) || dto.getNewPassword().length() < 6
                || dto.getNewPassword().length() > 32) {
            throw new BadRequestException("新密码长度需在 6~32 位之间");
        }
        // 2. 校验邮箱验证码（复用登录验证码：POST /user/code?email=xxx 发送，2 分钟有效）
        //    与登录不同：这里"验证码过期"和"验证码错误"都要拦，且先于查用户执行——
        //    避免攻击者用任意邮箱枚举注册状态
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + email);
        if (cacheCode == null) {
            throw new VerificationCodeException(MessageConstant.VERIFICATION_CODE_EXPIRED);
        }
        if (!cacheCode.equals(dto.getCode())) {
            throw new VerificationCodeException(MessageConstant.VERIFICATION_CODE_ERROR);
        }
        // 3. 验证码用后即删（防重放：同一个验证码不允许重置两次密码）
        stringRedisTemplate.delete(RedisConstants.LOGIN_CODE_KEY + email);
        // 4. 邮箱必须已注册（找回密码≠登录，不存在自动注册）
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            throw new BadRequestException("该邮箱尚未注册");
        }
        // 5. 重置密码（只更新 password 列）
        User update = new User();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(update);
        // 6. ★ 吊销登录态：删 Redis RefreshToken，盗号者手里的旧 token 全部作废
        stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + user.getId());
        log.info("邮箱:{} 找回密码成功，已吊销该用户全部登录态", email);
    }

    /**
     * 获取当前登录用户信息
     */
    @Override
    public Result<UserVO> me() {
        Long userId = UserHolder.getUser().getId();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getId, userId));
        if (user == null) {
            return Result.error("用户不存在");
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return Result.success(userVO);
    }

    /**
     * Feign 专用：按 userId 查询用户（昵称+头像），供其他服务跨服务调用
     */
    @Override
    public Result<UserVO> feignGetUser(Long userId) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getId, userId));
        if (user == null) {
            return Result.success();
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return Result.success(userVO);
    }


    private User addUserWithEmail(String email) {
        log.info("通过 email 新增用户:{}", email);
        //随机昵称
        String nickName = SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10);
        User user = User.builder().email(email).nickName(nickName).phone("").build();
        userMapper.insert(user);
        initUserInfo(user.getId());
        return user;
    }

    /** 手机号首次登录自动注册 */
    private User addUserWithPhone(String phone) {
        log.info("通过 phone 新增用户:{}", phone);
        String nickName = SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10);
        User user = User.builder().phone(phone).nickName(nickName).build();
        userMapper.insert(user);
        initUserInfo(user.getId());
        return user;
    }

    /**
     * 注册时同步初始化 user_info 行（详情资料表）。
     * 否则后续 PUT /user/info 的 updateById 匹配不到主键，影响 0 行且静默"成功"，资料永远保存不上。
     */
    private void initUserInfo(Long userId) {
        UserInfo info = new UserInfo();
        info.setUserId(userId);
        info.setFans(0);
        info.setFollowee(0);
        info.setCredits(0);
        userInfoService.save(info);
    }
    @Override
    public List<UserVO> selectUsersByIdsOrdered(List<Long> ids) {
        // 直接调用 Mapper 层的方法
        List<User> users = userMapper.selectUsersByIdsOrdered(ids);
        //user转vo
        List<UserVO> userVOs = users.stream()
                .map(user -> BeanUtil.copyProperties(user, UserVO.class))
                .collect(Collectors.toList());
        return userVOs;
    }

    @Override
    public Result<List<UserVO>> feignSearchUsers(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return Result.success(List.of());
        }
        // 手机号精确匹配（like %手机号% 没有意义且会泄露用户）、昵称模糊匹配
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .and(w -> w.like(User::getNickName, keyword).or().eq(User::getPhone, keyword))
                .last("limit 20"));
        List<UserVO> userVOs = users.stream()
                .map(user -> BeanUtil.copyProperties(user, UserVO.class))
                .collect(Collectors.toList());
        return Result.success(userVOs);
    }

    @Override
    public Result<Void> sign() {
        // 1. 获取当前用户id
        Long userId = UserHolder.getUser().getId();
        //获取日期
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;
        //获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // setBit 返回该位的旧值：true 说明今天已签到过，明确提示而不是重复记成功
        Boolean alreadySigned = stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        if (Boolean.TRUE.equals(alreadySigned)) {
            return Result.error("今日已签到，明天再来吧");
        }
        return Result.success();
    }

    @Override
    public Result<Integer> signCount() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.获取本月截止今天为止的所有的签到记录，返回的是一个十进制的数字 BITFIELD sign:5:202203 GET u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            // 没有任何签到结果
            return Result.success(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.success(0);
        }
        // 6.循环遍历
        int count = 0;
        while (true) {
            // 6.1.让这个数字与1做与运算，得到数字的最后一个bit位  // 判断这个bit位是否为0
            if ((num & 1) == 0) {
                // 如果为0，说明未签到，结束
                break;
            }else {
                // 如果不为0，说明已签到，计数器+1
                count++;
            }
            // 把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
        }
        return Result.success(count);
    }

    @Override
    public void updateInfo(UserInfo userInfo) {
        Long userId = UserHolder.getUser().getId();
        // 强制归属当前登录用户，防止越权修改他人资料
        userInfo.setUserId(userId);
        // 创建时间不允许被前端修改
        userInfo.setCreateTime(null);
        // 昵称存 user 表而非 user_info 表，先摘出透传字段单独更新，再置空避免 updateById 报"未知列"
        String nickName = userInfo.getNickName();
        userInfo.setNickName(null);
        if (StrUtil.isNotBlank(nickName)) {
            nickName = nickName.trim();
            if (nickName.length() > 16) {
                throw new BadRequestException("昵称不能超过 16 个字符");
            }
            User user = new User();
            user.setId(userId);
            user.setNickName(nickName);
            userMapper.updateById(user);
        }
        userInfoService.update(userInfo);
    }

    /**
     * 修改头像：头像存 user.images（所有脱敏 UserVO 从 user 表出，BlogVO/评论/关注列表等全站复用）。
     * 只 update 头像字段，避免覆盖其他列。
     */
    @Override
    public void updateAvatar(String image) {
        Long userId = UserHolder.getUser().getId();
        if (StrUtil.isBlank(image)) {
            throw new BadRequestException("头像地址不能为空");
        }
        User user = new User();
        user.setId(userId);
        user.setImages(image);
        userMapper.updateById(user);
    }

    @Override
    public FootprintSettingVO getFootprintSettings(Long userId) {
        String visible = stringRedisTemplate.opsForValue().get(RedisConstants.FOOTPRINT_VISIBLE_KEY + userId);
        String cleared = stringRedisTemplate.opsForValue().get(RedisConstants.FOOTPRINT_CLEARED_KEY + userId);
        return FootprintSettingVO.builder()
                // 未设置过开关时默认对他人可见
                .visible(!"0".equals(visible))
                .clearedTime(cleared == null ? 0L : Long.parseLong(cleared))
                .build();
    }

    @Override
    public void updateFootprintVisible(Boolean visible) {
        Long userId = UserHolder.getUser().getId();
        stringRedisTemplate.opsForValue().set(
                RedisConstants.FOOTPRINT_VISIBLE_KEY + userId,
                Boolean.TRUE.equals(visible) ? "1" : "0");
    }

    @Override
    public void clearFootprint() {
        Long userId = UserHolder.getUser().getId();
        // 不删订单（保留交易凭证），只记录清空时间戳：早于该时间的购物记录对他人隐藏
        stringRedisTemplate.opsForValue().set(
                RedisConstants.FOOTPRINT_CLEARED_KEY + userId,
                String.valueOf(System.currentTimeMillis()));
    }

    // ========== 每日积分 ==========

    @Override
    public Result<Integer> claimDailyCredits() {
        Long userId = UserHolder.getUser().getId();
        // 当天日期作为 key 后缀，天然按天隔离；TTL 到当天结束，过期自动清理
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String key = RedisConstants.CREDITS_CLAIM_KEY + userId + ":" + today;
        // setIfAbsent 保证并发下也只有一次领取成功
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", untilMidnight());
        if (!Boolean.TRUE.equals(first)) {
            throw new BadRequestException("今日积分已领取，明天再来吧");
        }
        int credits = userInfoService.addCredits(userId, RedisConstants.DAILY_CREDITS.intValue());
        return Result.success(credits);
    }

    @Override
    public Result<CreditsVO> getCredits() {
        Long userId = UserHolder.getUser().getId();
        UserInfo info = userInfoService.getById(userId);
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Boolean claimed = stringRedisTemplate.hasKey(RedisConstants.CREDITS_CLAIM_KEY + userId + ":" + today);
        return Result.success(CreditsVO.builder()
                .credits(info == null || info.getCredits() == null ? 0 : info.getCredits())
                .claimedToday(Boolean.TRUE.equals(claimed))
                .build());
    }

    /** 距今到当天 24:00 的剩余时长（领取标记的 TTL，跨日后 key 自动过期） */
    private Duration untilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, midnight);
    }
}
