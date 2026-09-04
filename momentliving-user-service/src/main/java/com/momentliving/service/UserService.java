package com.momentliving.service;

import com.momentliving.dto.LoginFormDTO;
import com.momentliving.entity.User;
import com.momentliving.entity.UserInfo;
import com.momentliving.result.Result;
import com.momentliving.vo.CaptchaVO;
import com.momentliving.vo.CreditsVO;
import com.momentliving.vo.FootprintSettingVO;
import com.momentliving.vo.LoginVO;
import com.momentliving.vo.UserVO;

import java.util.List;
import java.util.Map;

public interface UserService {

    /**
     * 发送验证码
     */
    void sendCode(String email);

    /**
     * 发送手机号验证码（演示模式：未接短信服务商，验证码直接返回前端展示）
     * @return CaptchaVO：code + 有效期秒数
     */
    CaptchaVO sendPhoneCode(String phone);

    /**
     * 登录功能
     */
    Result<LoginVO> loginByEmail(LoginFormDTO loginForm);

    /**
     * 刷新refreshtoken
     * @param refreshToken
     * @return
     */
    Result<Map<String, String>> refreshToken(String refreshToken);

    /**
     * 用户退出
     */
    void logout();

    /**
     * 获取当前登录用户信息
     */
    Result<UserVO> me();

    /**
     * Feign 专用：按 userId 查询用户（昵称+头像），供其他服务跨服务调用
     * @param userId 用户id
     * @return UserVO（仅 id/nickName/icon，不含 phone/password 等敏感字段）
     */
    Result<UserVO> feignGetUser(Long userId);

    /**
     * 根据ID列表查询用户，并按指定顺序返回
     * @param ids 用户ID列表
     * @return 排序后的用户列表
     */
    List<UserVO> selectUsersByIdsOrdered(List<Long> ids);

    /**
     * Feign 专用：搜用户（昵称模糊 or 手机号精确，limit 20）
     * @param keyword 关键词（昵称片段或完整手机号）
     * @return 脱敏 UserVO 列表（仅 id/nickName/images）
     */
    Result<List<UserVO>> feignSearchUsers(String keyword);

    Result<Void> sign();

    Result<Integer> signCount();

    /**
     * 更新当前登录用户的详细资料（城市/简介/生日/性别等）
     */
    void updateInfo(UserInfo userInfo);

    /**
     * 更新当前登录用户的头像（user.images，全站 UserVO 头像来源）
     * @param image 头像 URL（file-service 上传后的 OSS 地址）
     */
    void updateAvatar(String image);

    /**
     * 查询某用户的足迹可见性设置（visible 缺省 true；clearedTime 缺省 0）
     */
    FootprintSettingVO getFootprintSettings(Long userId);

    /**
     * 当前登录用户设置足迹是否对他人可见
     */
    void updateFootprintVisible(Boolean visible);

    /**
     * 当前登录用户清空足迹（记录清空时间戳，早于该时间的购物记录对他人隐藏）
     */
    void clearFootprint();

    /**
     * 领取每日积分（每天 1 次，每次 DAILY_CREDITS 分），返回累加后的总积分
     */
    Result<Integer> claimDailyCredits();

    /**
     * 查询当前登录用户积分及今日是否已领取
     */
    Result<CreditsVO> getCredits();
}
