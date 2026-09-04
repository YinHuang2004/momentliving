package com.momentliving.controller;

import com.momentliving.result.Result;
import com.momentliving.service.FollowService;
import com.momentliving.vo.FollowCountVO;
import com.momentliving.vo.UserVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private FollowService followService;

    /**
     * 关注/取关切换（PUT 本身是 toggle：已关注则取关、未关注则关注，前端无需先查状态）
     * 注：原 DELETE /follow/{id}"强制取关"是冗余实现，已删除；
     *    若未来需要"管理员强制解除用户关注"，单独加带权限校验的接口，不复用普通取关
     */
    @PutMapping("/{id}")
    public Result<Void> followUser(@PathVariable("id") Long followUserId) {
        followService.followUser(followUserId);
        return Result.success();
    }

    /**
     * 当前用户是否已关注该用户
     */
    @GetMapping("/is-follow/{id}")
    public Result<Boolean> isFollowed(@PathVariable("id") Long followUserId) {
        return Result.success(followService.isFollowed(followUserId));
    }

    /**
     * 关注/粉丝列表：type=1 我关注的人，type=2 我的粉丝
     */
    @GetMapping("/list/{type}")
    public Result<List<UserVO>> queryFollowList(@PathVariable("type") Integer type) {
        if (type == 1) {
            return Result.success(followService.queryFollowList());
        }
        if (type == 2) {
            return Result.success(followService.queryFanList());
        }
        return Result.error("type 参数错误：1=关注列表 2=粉丝列表");
    }

    /**
     * 某用户的关注/粉丝列表（他人主页"关注/粉丝"统计入口）
     * type=1 该用户关注的人，type=2 该用户的粉丝
     */
    @GetMapping("/list/{userId}/{type}")
    public Result<List<UserVO>> queryUserFollowList(@PathVariable("userId") Long userId,
                                                    @PathVariable("type") Integer type) {
        if (type == 1) {
            return Result.success(followService.queryFollowListOfUser(userId));
        }
        if (type == 2) {
            return Result.success(followService.queryFanListOfUser(userId));
        }
        return Result.error("type 参数错误：1=关注列表 2=粉丝列表");
    }

    /**
     * 某用户的关注数/粉丝数（个人主页展示用）
     */
    @GetMapping("/count/{userId}")
    public Result<FollowCountVO> queryFollowCount(@PathVariable("userId") Long userId) {
        return Result.success(followService.queryFollowCount(userId));
    }

    /**
     * 与某用户的共同关注
     */
    @GetMapping("/commons/{id}")
    public Result<List<UserVO>> getCommonFollow(@PathVariable("id") Long followUserId) {
        return Result.success(followService.getCommonFollow(followUserId));
    }

}
