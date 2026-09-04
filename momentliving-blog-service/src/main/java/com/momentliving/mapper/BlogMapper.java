package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.Blog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BlogMapper extends BaseMapper<Blog> {



    void updateLiked(Long id, int delta);


    List<Blog> selectBlogsByIds(List<Long> ids);
}
