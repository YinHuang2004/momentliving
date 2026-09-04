package com.momentliving.controller;

import com.momentliving.constant.SystemConstants;
import com.momentliving.dto.BlogDTO;
import com.momentliving.dto.ScrollResult;
import com.momentliving.result.Result;
import com.momentliving.service.BlogService;
import com.momentliving.vo.BlogVO;
import com.momentliving.vo.ShopSimpleVO;
import com.momentliving.vo.UserVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private BlogService blogService;

    @PostMapping
    public Result<Long> saveBlog(@RequestBody BlogDTO blog) {

        // 保存探店博文
        Long id = blogService.saveBlog(blog);
        // 返回 id
        return Result.success(id);
    }

    /**
     * 当前登录用户可发布博客的店铺（购买过券的店铺，发布页选择用）
     */
    @GetMapping("/purchasable-shops")
    public Result<List<ShopSimpleVO>> purchasableShops() {
        return Result.success(blogService.purchasableShops());
    }

    @PutMapping("/like/{id}")
    public Result<Void> likeBlog(@PathVariable("id") Long id) {
        blogService.likeBlog(id);
        return Result.success();
    }
    @GetMapping("/likes/{id}")
    public Result<List<UserVO>> queryBlogLikes(@PathVariable("id") Long id) {

        return blogService.queryBlogLikes(id);
    }

    /**
     * 收藏/取消收藏博客（toggle：返回 true=操作后已收藏）
     */
    @PutMapping("/favorite/{id}")
    public Result<Boolean> favoriteBlog(@PathVariable("id") Long id) {
        return Result.success(blogService.favoriteBlog(id));
    }

    /**
     * 当前用户是否已收藏该博客（博客详情页收藏按钮状态）
     */
    @GetMapping("/favorite/is-favorite/{id}")
    public Result<Boolean> isFavorite(@PathVariable("id") Long id) {
        return Result.success(blogService.isFavorite(id));
    }

    /**
     * 我收藏的博客列表（收藏时间倒序）
     */
    @GetMapping("/of/favorite")
    public Result<List<BlogVO>> myFavoriteBlogs() {
        return Result.success(blogService.myFavoriteBlogs());
    }

    /**
     * 我点赞过的博客列表（点赞时间倒序）
     */
    @GetMapping("/of/likes")
    public Result<List<BlogVO>> myLikedBlogs() {
        return Result.success(blogService.myLikedBlogs());
    }

    @GetMapping("/of/user/{userId}")
    public Result<List<BlogVO>> queryMyBlog(@PathVariable("userId") Long userId,
                                            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        List<BlogVO> records = blogService.queryUserBlogs(userId, current, SystemConstants.MAX_PAGE_SIZE);
        return Result.success(records);
    }

    @GetMapping("/hot")
    public Result<List<BlogVO>> queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        List<BlogVO> records = blogService.queryHotBlog(current, SystemConstants.MAX_PAGE_SIZE);
        return Result.success(records);
    }

    @GetMapping("/{id}")
    public Result<BlogVO> queryBlogById(@PathVariable("id") Long id) {
        BlogVO blog = blogService.queryBlogById(id);
        if (blog == null) {
            return Result.error("博客不存在");
        }
        return Result.success(blog);
    }
    @GetMapping("/of/follow")
    public Result<ScrollResult> queryBlogOfFollow(
            @RequestParam("lastId") Long max, @RequestParam(value = "offset", defaultValue = "0") Integer offset){
        return blogService.queryBlogOfFollow(max, offset);
    }

    @PutMapping("/{id}")
    public Result<Void> updateBlog(@PathVariable("id") Long id, @RequestBody BlogDTO blog) {
        blog.setId(id);
        blogService.updateBlog(blog);
        return Result.success();
    }

    /**
     * 删除单个博客（内部复用批量删除逻辑）
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteBlog(@PathVariable("id") Long id) {
        blogService.deleteBlogs(List.of(id));
        return Result.success();
    }

    /**
     * 批量删除博客：请求体传 id 数组，如 [1, 2, 3]；单个删除也走这个接口（数组里放一个 id）
     */
    @DeleteMapping
    public Result<Void> deleteBlogs(@RequestBody List<Long> ids) {
        blogService.deleteBlogs(ids);
        return Result.success();
    }

}
