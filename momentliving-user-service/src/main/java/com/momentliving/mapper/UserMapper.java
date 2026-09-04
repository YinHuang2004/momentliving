package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.User;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    // 传入一个 List<Long> ids，返回按 ids 顺序排好的 User 列表
    List<User> selectUsersByIdsOrdered(List<Long> ids);

}
