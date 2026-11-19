package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.BlogFavorite;
import org.apache.ibatis.annotations.Mapper;

/** 博客收藏记录表 Mapper */
@Mapper
public interface BlogFavoriteMapper extends BaseMapper<BlogFavorite> {
}
