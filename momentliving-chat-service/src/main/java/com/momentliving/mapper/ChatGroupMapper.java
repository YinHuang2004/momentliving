package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.ChatGroup;
import org.apache.ibatis.annotations.Mapper;

/** 聊天群表 Mapper */
@Mapper
public interface ChatGroupMapper extends BaseMapper<ChatGroup> {
}
