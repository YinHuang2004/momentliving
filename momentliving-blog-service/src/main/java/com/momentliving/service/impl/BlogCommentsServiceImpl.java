package com.momentliving.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.momentliving.context.UserHolder;
import com.momentliving.entity.BlogComments;
import com.momentliving.mapper.BlogCommentsMapper;
import com.momentliving.service.BlogCommentsService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/** 评论业务实现：发表（归属当前用户）、分页查询、删除（仅作者本人） */
@Service
public class BlogCommentsServiceImpl implements BlogCommentsService {

    @Resource
    private BlogCommentsMapper blogCommentsMapper;

    @Override
    public Long save(BlogComments blogComments) {
        // 评论归属当前登录用户，防止越权
        blogComments.setUserId(UserHolder.getUser().getId());
        // 一级评论兜底：parent_id/answer_id 列 NOT NULL，约定 0=无父级（见表注释）；
        // MyBatis-Plus insert 对 null 字段直接省略，不补 0 会触发 "Field 'parent_id' doesn't have a default value"
        if (blogComments.getParentId() == null) {
            blogComments.setParentId(0L);
        }
        if (blogComments.getAnswerId() == null) {
            blogComments.setAnswerId(0L);
        }
        blogCommentsMapper.insert(blogComments);
        return blogComments.getId();
    }

    @Override
    public BlogComments getById(Long id) {
        return blogCommentsMapper.selectById(id);
    }

    /** 某博客的评论列表：分页，按时间倒序 */
    @Override
    public List<BlogComments> queryPage(Long blogId, Integer current, Integer pageSize) {
        LambdaQueryWrapper<BlogComments> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BlogComments::getBlogId, blogId)
                .orderByDesc(BlogComments::getCreateTime);
        return blogCommentsMapper.selectPage(new Page<>(current, pageSize), wrapper).getRecords();
    }

    /** 删除评论：先查存在性再校验归属，越权/不存在均抛 IllegalArgumentException */
    @Override
    public void deleteById(Long id) {
        Long userId = UserHolder.getUser().getId();
        BlogComments comment = blogCommentsMapper.selectById(id);
        if (comment == null) {
            throw new IllegalArgumentException("评论不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new IllegalArgumentException("只能删除自己的评论");
        }
        blogCommentsMapper.deleteById(id);
    }
}
