package com.momentliving.service;

import com.momentliving.entity.Follow;
import com.momentliving.vo.FollowCountVO;
import com.momentliving.vo.UserVO;

import java.util.List;

public interface FollowService {

    /**
     * 关注/取关切换（幂等，根据当前状态自动判断）
     */
    void followUser(Long followUserId);

    /**
     * 当前用户是否已关注该用户
     */
    boolean isFollowed(Long followUserId);

    /**
     * 某用户的关注数/粉丝数（个人主页展示用）
     */
    FollowCountVO queryFollowCount(Long userId);

    /**
     * 我关注的人列表
     */
    List<UserVO> queryFollowList();

    /**
     * 我的粉丝列表
     */
    List<UserVO> queryFanList();

    /**
     * 某用户关注的人列表（他人主页"关注"统计入口）
     */
    List<UserVO> queryFollowListOfUser(Long userId);

    /**
     * 某用户的粉丝列表（他人主页"粉丝"统计入口）
     */
    List<UserVO> queryFanListOfUser(Long userId);

    List<UserVO> getCommonFollow(Long followUserId);
}
