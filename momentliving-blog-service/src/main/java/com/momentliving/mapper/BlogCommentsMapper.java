package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.BlogComments;
import org.apache.ibatis.annotations.Mapper;

/** 博客评论表 Mapper */
@Mapper
public interface BlogCommentsMapper extends BaseMapper<BlogComments> {
}
