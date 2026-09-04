package com.momentliving.controller;

import com.momentliving.constant.SystemConstants;
import com.momentliving.entity.BlogComments;
import com.momentliving.result.Result;
import com.momentliving.service.BlogCommentsService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    @Resource
    private BlogCommentsService blogCommentsService;

    /**
     * 发表评论
     */
    @PostMapping
    public Result<Long> addComment(@RequestBody BlogComments blogComments) {
        Long id = blogCommentsService.save(blogComments);
        return Result.success(id);
    }

    /**
     * 评论详情
     */
    @GetMapping("/{id}")
    public Result<BlogComments> queryCommentById(@PathVariable("id") Long id) {
        BlogComments comment = blogCommentsService.getById(id);
        return comment == null ? Result.error("评论不存在") : Result.success(comment);
    }

    /**
     * 某博客的评论列表（分页，按时间倒序）
     */
    @GetMapping("/list/{blogId}")
    public Result<List<BlogComments>> queryCommentsOfBlog(@PathVariable("blogId") Long blogId,
                                         @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return Result.success(blogCommentsService.queryPage(blogId, current, SystemConstants.MAX_PAGE_SIZE));
    }

    /**
     * 删除评论（仅作者本人）
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteComment(@PathVariable("id") Long id) {
        blogCommentsService.deleteById(id);
        return Result.success();
    }
}
