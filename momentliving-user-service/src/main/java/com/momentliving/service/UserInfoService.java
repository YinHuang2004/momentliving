package com.momentliving.service;

import com.momentliving.entity.UserInfo;

/** 用户详情（user_info 表）服务：详情行读写（upsert）与积分累加 */
public interface UserInfoService {

    void save(UserInfo userInfo);

    UserInfo getById(Long userId);

    void update(UserInfo userInfo);

    /**
     * 积分原子累加；用户无 user_info 行时降级为插入（初始积分为本次累加值）
     * @return 累加后的最新积分
     */
    int addCredits(Long userId, int points);
}
