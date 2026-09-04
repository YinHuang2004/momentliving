package com.momentliving.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.momentliving.api.client.UserClient;
import com.momentliving.constant.RedisConstants;
import com.momentliving.context.UserHolder;
import com.momentliving.entity.Follow;
import com.momentliving.exception.BaseException;
import com.momentliving.mapper.FollowMapper;
import com.momentliving.service.FollowService;
import com.momentliving.vo.FollowCountVO;
import com.momentliving.vo.UserVO;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FollowServiceImpl implements FollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private FollowMapper followMapper;
    @Resource
    private UserClient userClient;

    @Override
    public void followUser(Long followUserId) {
        //获取当前用户ID
        Long userId = UserHolder.getUser().getId();
        //不能关注自己
        if (followUserId.equals(userId)) {
            throw new BaseException("不能关注自己");
        }
        // 以 DB 为准判断当前状态（Redis 集合只是缓存，冷缓存/被清后凭它判断会重复插入或误取关）
        Boolean isFollowed = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, followUserId)) > 0;
        if (isFollowed) {
            // 从数据库中删除关注记录
            LambdaUpdateWrapper<Follow> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Follow::getUserId, userId).eq(Follow::getFollowUserId, followUserId);
            followMapper.delete(wrapper);
            // 已关注，则取消关注
            stringRedisTemplate.opsForSet().remove(RedisConstants.FOLLOW_USER_KEY + userId, followUserId.toString());


        }
        else{
            // 从数据库中添加关注记录
            Follow follow = Follow.builder()
                    .userId(userId)
                    .followUserId(followUserId)
                    .createTime(LocalDateTime.now())
                    .build();
            followMapper.insert(follow);
            // 未关注，则关注
            stringRedisTemplate.opsForSet().add(RedisConstants.FOLLOW_USER_KEY + userId, followUserId.toString());

        }

    }

    @Override
    public boolean isFollowed(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FOLLOW_USER_KEY + userId;
        // 集合不存在（冷缓存：历史关注数据 / Redis 被清）时先回查 DB 预热，避免把已关注误判成未关注
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(key))) {
            warmFollowSet(userId, key);
        }
        Boolean member = stringRedisTemplate.opsForSet().isMember(key, followUserId.toString());
        return Boolean.TRUE.equals(member);
    }

    @Override
    public FollowCountVO queryFollowCount(Long userId) {
        // 以数据库为准（Redis 集合只存了"我关注的人"，且可能未预热）
        Long followee = followMapper.selectCount(
                new LambdaQueryWrapper<Follow>().eq(Follow::getUserId, userId));
        Long follower = followMapper.selectCount(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowUserId, userId));
        return FollowCountVO.builder()
                .followee(followee == null ? 0 : followee)
                .follower(follower == null ? 0 : follower)
                .build();
    }

    @Override
    public List<UserVO> queryFollowList() {
        // 我关注的人：条件用 user_id，取值用 follow_user_id
        return queryUsersByFollowCondition(
                UserHolder.getUser().getId(),
                Follow::getUserId,           // 条件：user_id = 我
                Follow::getFollowUserId      // 取值：取 follow_user_id（即我关注的人）
        );
    }

    @Override
    public List<UserVO> queryFanList() {
        // 我的粉丝：条件用 follow_user_id，取值用 user_id
        return queryUsersByFollowCondition(
                UserHolder.getUser().getId(),
                Follow::getFollowUserId,     // 条件：follow_user_id = 我
                Follow::getUserId            // 取值：取 user_id（即关注我的人，也就是粉丝）
        );
    }

    @Override
    public List<UserVO> queryFollowListOfUser(Long userId) {
        // TA 关注的人：条件 user_id = TA，取 follow_user_id
        return queryUsersByFollowCondition(userId, Follow::getUserId, Follow::getFollowUserId);
    }

    @Override
    public List<UserVO> queryFanListOfUser(Long userId) {
        // TA 的粉丝：条件 follow_user_id = TA，取 user_id
        return queryUsersByFollowCondition(userId, Follow::getFollowUserId, Follow::getUserId);
    }

    /**
     * 通用方法：根据 Follow 表查询某用户的关注/粉丝列表
     * @param userId 目标用户 id（查自己时传当前登录人，查他人主页时传该用户）
     * @param conditionField 查询条件字段（如 Follow::getUserId 或 Follow::getFollowUserId）
     * @param getUserIdFunc  从 Follow 对象中提取目标用户id的方法
     * @return 用户VO列表
     */
    private List<UserVO> queryUsersByFollowCondition(
            Long userId,
            SFunction<Follow, Long> conditionField,
            Function<Follow, Long> getUserIdFunc) {

        // 1. 构造查询条件：conditionField = userId，按创建时间倒序
        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(conditionField, userId)
                .orderByDesc(Follow::getCreateTime);

        // 2. 执行查询
        List<Follow> follows = followMapper.selectList(wrapper);

        // 3. 如果为空，直接返回空列表（防止远程调用报错）
        if (follows.isEmpty()) {
            return Collections.emptyList();
        }

        // 4. 提取目标用户id列表
        List<Long> userIds = follows.stream()
                .map(getUserIdFunc)
                .collect(Collectors.toList());

        // 5. 调用远程接口查用户信息，并直接返回
        return userClient.selectUsersByIdsOrdered(userIds).getData();
    }


    @Override
    public List<UserVO> getCommonFollow(Long followUserId) {
        //获取当前用户ID
        Long userId = UserHolder.getUser().getId();
        String key1 = RedisConstants.FOLLOW_USER_KEY + userId;
        String key2 = RedisConstants.FOLLOW_USER_KEY + followUserId;
        //取交集（快路径）
        Set<String> commonFollow = stringRedisTemplate.opsForSet().intersect(key1, key2);
        //空集兜底：Redis 集合是"发生关注动作时"才写入的缓存，历史关注数据 / Redis 被清后集合缺失，
        //交集恒为空 → 回查 DB 重算，并顺带把双方集合预热回 Redis（下次走快路径）
        if (commonFollow == null || commonFollow.isEmpty()) {
            commonFollow = loadCommonFromDb(userId, followUserId, key1, key2);
        }
        //如果没有交集，则返回空列表
        if(commonFollow.isEmpty()){
            return Collections.emptyList();
        }
        //将String解析为Long类型
        List<Long> userIdList = commonFollow.stream().map(s -> Long.valueOf(s)).collect(Collectors.toList());
        //从数据库查询用户
        List<UserVO> users = userClient.selectUsersByIdsOrdered(userIdList).getData()
                .stream().map(user-> BeanUtil.copyProperties(user,UserVO.class))
                .collect(Collectors.toList());
        // 从redis中获取关注列表
        return users;
    }

    /**
     * DB 兜底：把双方在 DB 里的全量关注对象预热进 Redis 集合（sAdd 幂等），再重新求交集。
     * 预热后交集与 DB 强一致，修复"缓存冷导致共同关注永远为空"的问题。
     */
    private Set<String> loadCommonFromDb(Long userId, Long followUserId, String key1, String key2) {
        warmFollowSet(userId, key1);
        warmFollowSet(followUserId, key2);
        Set<String> common = stringRedisTemplate.opsForSet().intersect(key1, key2);
        return common == null ? Collections.emptySet() : common;
    }

    /** 把某用户在 DB 里的全量关注对象写入 Redis 集合（幂等，无关注记录时不写空集合） */
    private void warmFollowSet(Long userId, String key) {
        List<Long> followeeIds = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getUserId, userId)
                        .select(Follow::getFollowUserId))
                .stream().map(Follow::getFollowUserId).collect(Collectors.toList());
        if (!followeeIds.isEmpty()) {
            stringRedisTemplate.opsForSet().add(key,
                    followeeIds.stream().map(String::valueOf).toArray(String[]::new));
        }
    }
}
