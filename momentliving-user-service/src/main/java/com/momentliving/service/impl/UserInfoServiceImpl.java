package com.momentliving.service.impl;

import com.momentliving.entity.UserInfo;
import com.momentliving.mapper.UserInfoMapper;
import com.momentliving.service.UserInfoService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class UserInfoServiceImpl implements UserInfoService {

    @Resource
    private UserInfoMapper userInfoMapper;

    @Override
    public void save(UserInfo userInfo) {
        userInfoMapper.insert(userInfo);
    }

    @Override
    public UserInfo getById(Long userId) {
        return userInfoMapper.selectById(userId);
    }

    @Override
    public void update(UserInfo userInfo) {
        // upsert：老用户可能没有 user_info 行（历史注册未初始化），updateById 影响 0 行时降级为插入。
        // 注意 updateById 对不存在的主键是"影响 0 行且不报错"，必须检查返回值
        int rows = userInfoMapper.updateById(userInfo);
        if (rows == 0) {
            userInfoMapper.insert(userInfo);
        }
    }

    @Override
    public int addCredits(Long userId, int points) {
        int rows = userInfoMapper.addCredits(userId, points);
        if (rows == 0) {
            // 历史用户无 user_info 行：插入一行，初始积分即本次累加值
            UserInfo info = new UserInfo();
            info.setUserId(userId);
            info.setFans(0);
            info.setFollowee(0);
            info.setCredits(points);
            userInfoMapper.insert(info);
            return points;
        }
        return userInfoMapper.selectById(userId).getCredits();
    }
}
