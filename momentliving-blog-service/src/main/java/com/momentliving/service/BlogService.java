package com.momentliving.service;

import com.momentliving.dto.BlogDTO;
import com.momentliving.dto.ScrollResult;
import com.momentliving.result.Result;
import com.momentliving.vo.BlogVO;
import com.momentliving.vo.ShopSimpleVO;
import com.momentliving.vo.UserVO;

import java.util.List;

/** 探店博客业务接口：发布、点赞、收藏、查询与 Feed 流 */
public interface BlogService {

    /** 发布探店笔记：三重校验（shopId 必填/购买过/店铺存在）后落库，再推送粉丝收件箱 */
    Long saveBlog(BlogDTO blog);

    /**
     * 当前登录用户可发布博客的店铺列表（购买过券的店铺，仅 id + 名称）
     */
    List<ShopSimpleVO> purchasableShops();

    /** 点赞/取消点赞（toggle）：双 ZSet 索引 + DB 点赞数原子增减 */
    void likeBlog(Long id);

    /** 某用户的博客列表（分页，时间倒序） */
    List<BlogVO> queryUserBlogs(Long userId, Integer current, Integer pageSize);

    /** 热门博客（按点赞数倒序分页） */
    List<BlogVO> queryHotBlog(Integer current, Integer pageSize);

    /** 博客详情（含作者信息/当前用户点赞/收藏状态） */
    BlogVO queryBlogById(Long id);

    /** 博客的点赞用户列表（按点赞时间正序取前 5） */
    Result<List<UserVO>> queryBlogLikes(Long id);

    /** 关注动态流：Feed 收件箱滚动分页（max=上次最小时间戳，offset=同分偏移） */
    Result<ScrollResult> queryBlogOfFollow(Long max, Integer offset);

    /**
     * 更新博客（仅作者本人，且只允许更新标题/内容/图片）
     */
    void updateBlog(BlogDTO blog);

    /**
     * 批量删除博客（单个删除传单元素集合即可，仅作者本人，联动清理评论/点赞缓存/粉丝收件箱）
     */
    void deleteBlogs(List<Long> ids);

    /**
     * 收藏/取消收藏博客（toggle：未收藏则收藏、已收藏则取消，返回 true=操作后已收藏）
     */
    Boolean favoriteBlog(Long blogId);

    /**
     * 当前登录用户是否已收藏该博客（未登录返回 false）
     */
    Boolean isFavorite(Long blogId);

    /**
     * 我收藏的博客列表（按收藏时间倒序）
     */
    List<BlogVO> myFavoriteBlogs();

    /**
     * 我点赞过的博客列表（按点赞时间倒序，数据源为 Redis 反向索引 blog:my:liked:{userId}）
     */
    List<BlogVO> myLikedBlogs();
}
