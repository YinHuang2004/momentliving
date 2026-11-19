package com.momentliving.service;

import com.momentliving.entity.BlogComments;

import java.util.List;

/** 博客评论业务接口 */
public interface BlogCommentsService {

    /**
     * 发表评论（自动带当前登录用户）
     */
    Long save(BlogComments blogComments);

    /**
     * 评论详情
     */
    BlogComments getById(Long id);

    /**
     * 某博客的评论列表（按时间倒序分页）
     */
    List<BlogComments> queryPage(Long blogId, Integer current, Integer pageSize);

    /**
     * 删除评论（仅作者本人）
     */
    void deleteById(Long id);
}
