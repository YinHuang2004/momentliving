package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.BlogComments;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BlogCommentsMapper extends BaseMapper<BlogComments> {
}
