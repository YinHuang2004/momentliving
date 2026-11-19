package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.Blog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 博客表 Mapper：点赞数原子增减、按 id 批量查询 */
@Mapper
public interface BlogMapper extends BaseMapper<Blog> {



    /** 点赞数原子增减（delta 为 +1/-1，避免并发下读改写丢失更新） */
    void updateLiked(Long id, int delta);


    /** 按 id 批量查博客（SQL 用 ORDER BY FIELD 保证按传入顺序返回） */
    List<Blog> selectBlogsByIds(List<Long> ids);
}
