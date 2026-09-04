package com.momentliving.controller;

import com.momentliving.dto.LoginFormDTO;
import com.momentliving.context.UserHolder;
import com.momentliving.entity.UserInfo;
import com.momentliving.result.Result;
import com.momentliving.service.UserInfoService;
import com.momentliving.service.UserService;
import com.momentliving.vo.CaptchaVO;
import com.momentliving.vo.CreditsVO;
import com.momentliving.vo.FootprintSettingVO;
import com.momentliving.vo.LoginVO;
import com.momentliving.vo.UserVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private UserInfoService userInfoService;


    /**
     * 发送验证码（email 与 phone 二选一）
     * - email：走 SMTP 邮件发送，code 返回 null
     * - phone：演示模式（未接短信），验证码直接返回前端展示，有效期见 expireSeconds
     */
    @PostMapping("code")
    public Result<CaptchaVO> sendCode(@RequestParam(value = "email", required = false) String email,
                                      @RequestParam(value = "phone", required = false) String phone) {
        if (phone != null && !phone.isBlank()) {
            log.info("用户:{}请求手机号登录，发送验证码中（演示模式）", phone);
            return Result.success(userService.sendPhoneCode(phone));
        }
        log.info("用户:{}请求邮箱登录，发送验证码中", email);
        userService.sendCode(email);
        return Result.success(new CaptchaVO());
    }

    /**
     * 登录功能
     * @param loginForm 登录参数
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginFormDTO loginForm) {
        log.info("用户来登录啦:{}", loginForm);

        return userService.loginByEmail(loginForm);
    }
    @PostMapping("/refresh")
    public Result<Map<String, String>> refreshToken(@RequestParam("refreshToken") String refreshToken) {
        return userService.refreshToken(refreshToken);
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return userService.me();
    }

    /**
     * Feign 专用：按 userId 查询用户（昵称+头像），供其他服务跨服务调用
     */
    @GetMapping("/feign/{id}")
    public Result<UserVO> feignGetUser(@PathVariable("id") Long id) {
        return userService.feignGetUser(id);
    }

    @PostMapping("/logout")
    public Result<Void> logout(){
        log.info("用户退出");
        userService.logout();
        return Result.success();
    }

    @GetMapping("/info/{id}")
    public Result<UserInfo> info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.success();
        }
        // 回填昵称/头像：二者实际存 user 表（UserInfo 上是 @TableField(exist=false) 透传字段，
        // getById 只查 user_info 表不会填充），不回填前端会一直显示"加载中…"
        Result<UserVO> uv = userService.feignGetUser(userId);
        if (uv != null && uv.getData() != null) {
            info.setNickName(uv.getData().getNickName());
            info.setImages(uv.getData().getImages());
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.success(info);
    }
    @PostMapping("/sign")
    public Result<Void> sign(){
        return userService.sign();
    }
    @GetMapping("/sign/count")
    public Result<Integer> signCount(){
        return userService.signCount();
    }

    /**
     * 领取每日积分（每天 1 次，每次 10 分，累计到 user_info.credits）
     */
    @PostMapping("/credits/claim")
    public Result<Integer> claimDailyCredits(){
        return userService.claimDailyCredits();
    }

    /**
     * 查询当前登录用户积分及今日是否已领取
     */
    @GetMapping("/credits")
    public Result<CreditsVO> getCredits(){
        return userService.getCredits();
    }

    /**
     * 更新当前登录用户的详细资料（城市/简介/生日/性别等）
     */
    @PutMapping("/info")
    public Result<Void> updateInfo(@RequestBody UserInfo userInfo){
        userService.updateInfo(userInfo);
        return Result.success();
    }

    /**
     * 修改当前登录用户头像（image 为 file-service 上传后的 URL）
     */
    @PutMapping("/avatar")
    public Result<Void> updateAvatar(@RequestParam("image") String image){
        userService.updateAvatar(image);
        return Result.success();
    }
    @GetMapping("/feign/select")
    public Result<List<UserVO>> selectUsersByIdsOrdered(@RequestParam("ids") List<Long> ids){
        return Result.success(userService.selectUsersByIdsOrdered(ids));
    }

    /**
     * Feign 专用：搜用户（chat-service 的 /chat/users/search 代理调用）
     * 昵称模糊匹配 or 手机号精确匹配，最多 20 条；返回脱敏 UserVO
     */
    @GetMapping("/feign/search")
    public Result<List<UserVO>> searchUsers(@RequestParam("keyword") String keyword) {
        return userService.feignSearchUsers(keyword);
    }

    // ========== 个人主页足迹（购物记录）设置 ==========

    /**
     * 当前登录用户的足迹设置（可见开关 + 清空时间戳）
     */
    @GetMapping("/footprint/settings")
    public Result<FootprintSettingVO> footprintSettings() {
        Long userId = UserHolder.getUser().getId();
        return Result.success(userService.getFootprintSettings(userId));
    }

    /**
     * 设置足迹是否对他人可见（个人主页"足迹"开关）
     */
    @PutMapping("/footprint/visible")
    public Result<Void> updateFootprintVisible(@RequestParam("visible") Boolean visible) {
        userService.updateFootprintVisible(visible);
        return Result.success();
    }

    /**
     * 清空足迹：记录清空时间戳，早于该时间的购物记录对他人隐藏（订单本身保留）
     */
    @PostMapping("/footprint/clear")
    public Result<Void> clearFootprint() {
        userService.clearFootprint();
        return Result.success();
    }

    /**
     * Feign 专用：查某用户的足迹设置（voucher-service 判断他人足迹是否可见时经 UserClient 调用）
     */
    @GetMapping("/feign/footprint/{userId}")
    public Result<FootprintSettingVO> feignFootprintSettings(@PathVariable("userId") Long userId) {
        return Result.success(userService.getFootprintSettings(userId));
    }
}
