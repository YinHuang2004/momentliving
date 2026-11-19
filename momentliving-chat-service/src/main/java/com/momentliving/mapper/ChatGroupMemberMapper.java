package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.ChatGroupMember;
import org.apache.ibatis.annotations.Mapper;

/** 群成员表 Mapper（role：0 成员 1 管理 2 群主） */
@Mapper
public interface ChatGroupMemberMapper extends BaseMapper<ChatGroupMember> {
}
