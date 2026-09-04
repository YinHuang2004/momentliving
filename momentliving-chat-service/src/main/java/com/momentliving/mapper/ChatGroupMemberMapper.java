package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.ChatGroupMember;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatGroupMemberMapper extends BaseMapper<ChatGroupMember> {
}
