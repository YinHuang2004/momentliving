package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.UserInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {

    /**
     * 积分原子累加（credits = credits + n），避免"查出再加"的并发丢失更新
     * @return 影响行数，0 表示该用户没有 user_info 行（历史注册未初始化）
     */
    @Update("UPDATE user_info SET credits = credits + #{points} WHERE user_id = #{userId}")
    int addCredits(@Param("userId") Long userId, @Param("points") int points);
}
