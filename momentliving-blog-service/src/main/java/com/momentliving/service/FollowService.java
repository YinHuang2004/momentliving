package com.momentliving.service;

import com.momentliving.entity.Follow;
import com.momentliving.vo.FollowCountVO;
import com.momentliving.vo.UserVO;

import java.util.List;

/** 关注关系业务接口：关注/取关、列表、统计与共同关注 */
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

    /** 与某用户的共同关注（Redis 集合求交集，空集时回查 DB 并预热缓存） */
    List<UserVO> getCommonFollow(Long followUserId);
}
