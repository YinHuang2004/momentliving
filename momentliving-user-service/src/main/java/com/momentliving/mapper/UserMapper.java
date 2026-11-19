package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.User;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** user 表 Mapper（用户账号主体：手机号/邮箱/密码/昵称/头像），含按 ids 顺序批量查询 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    // 传入一个 List<Long> ids，返回按 ids 顺序排好的 User 列表
    List<User> selectUsersByIdsOrdered(List<Long> ids);

}
