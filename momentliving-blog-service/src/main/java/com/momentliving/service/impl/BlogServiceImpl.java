package com.momentliving.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.momentliving.api.client.ShopClient;
import com.momentliving.api.client.UserClient;
import com.momentliving.api.client.VoucherClient;
import com.momentliving.constant.RedisConstants;
import com.momentliving.context.UserHolder;
import com.momentliving.dto.BlogDTO;
import com.momentliving.dto.ScrollResult;
import com.momentliving.entity.Blog;
import com.momentliving.entity.BlogComments;
import com.momentliving.entity.BlogFavorite;
import com.momentliving.entity.Follow;
import com.momentliving.exception.BadRequestException;

import com.momentliving.mapper.BlogCommentsMapper;
import com.momentliving.mapper.BlogFavoriteMapper;
import com.momentliving.mapper.BlogMapper;
import com.momentliving.mapper.FollowMapper;

import com.momentliving.result.Result;
import com.momentliving.service.BlogService;
import com.momentliving.vo.BlogVO;
import com.momentliving.vo.ShopSimpleVO;
import com.momentliving.vo.UserVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BlogServiceImpl implements BlogService {

    @Resource
    private BlogMapper blogMapper;
    @Resource
    private BlogCommentsMapper blogCommentsMapper;
    @Resource
    private BlogFavoriteMapper blogFavoriteMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private FollowMapper followMapper;
    @Resource
    private UserClient userClient;
    @Resource
    private VoucherClient voucherClient;
    @Resource
    private ShopClient shopClient;

    @Override
    public Long saveBlog(BlogDTO dto) {
        // 获取登录用户
        UserVO user = UserHolder.getUser();
        // ★ 关联店铺必填，且必须是用户购买过（订单已支付/已核销）的店铺：
        //   blog.shop_id 非空无默认值，同时防"云探店"发假笔记
        Long shopId = dto.getShopId();
        if (shopId == null) {
            throw new BadRequestException("请选择要分享的店铺");
        }
        List<Long> purchasedShopIds = voucherClient.userPurchasedShopIds(user.getId()).getData();
        if (purchasedShopIds == null || !purchasedShopIds.contains(shopId)) {
            throw new BadRequestException("只能发布自己购买过券的店铺");
        }
        if (shopClient.getShop(shopId).getData() == null) {
            throw new BadRequestException("关联店铺不存在");
        }
        Blog blog = BeanUtil.copyProperties(dto, Blog.class);
        blog.setUserId(user.getId());
        blog.setShopId(shopId);
        blogMapper.insert(blog);
        //查询当前用户的所有粉丝
        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Follow::getFollowUserId, user.getId());
        List<Follow> follows = followMapper.selectList(wrapper);
        // 4.推送笔记id给所有粉丝
        for (Follow follow : follows) {
            // 4.1.获取粉丝id
            Long userId = follow.getUserId();
            // 4.2.推送
            String key = RedisConstants.FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        return blog.getId();
    }

    /**
     * 当前登录用户可发布博客的店铺：voucher-service 查购买过的 shopId 集合，shop-service 回填名称。
     * 发布校验（saveBlog）与本列表同源，保证"只能选、也只能发买过的店"。
     */
    @Override
    public List<ShopSimpleVO> purchasableShops() {
        Long userId = UserHolder.getUser().getId();
        List<Long> shopIds = voucherClient.userPurchasedShopIds(userId).getData();
        if (shopIds == null || shopIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 购买过的店铺数量有限（每个店铺一条），逐个查详情可接受；异常店铺跳过不阻断
        List<ShopSimpleVO> shops = new ArrayList<>(shopIds.size());
        for (Long shopId : shopIds) {
            try {
                com.momentliving.vo.ShopVO shop = shopClient.getShop(shopId).getData();
                if (shop != null) {
                    shops.add(new ShopSimpleVO(shop.getId(), shop.getName()));
                }
            } catch (Exception e) {
                log.warn("查询可发布店铺详情失败，跳过 shopId={}", shopId, e);
            }
        }
        return shops;
    }

    /**
     * 对博客进行点赞
     */
    @Override
    public void likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        //判断当前用户是否点赞过
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score=stringRedisTemplate.opsForZSet().score(key, userId.toString());


        if (score == null) {
            //当前用户没有点赞过
            blogMapper.updateLiked(id, 1); // 做原子加
            long now = System.currentTimeMillis();
            stringRedisTemplate.opsForZSet().add(key, userId.toString(), now);
            // 反向索引（member=blogId score=点赞时间戳）："我的喜欢"列表按此倒序取
            stringRedisTemplate.opsForZSet().add(RedisConstants.BLOG_MY_LIKED_KEY + userId, id.toString(), now);
        } else {
            // 取消点赞同理
            blogMapper.updateLiked(id, -1);    // 做原子减
            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            stringRedisTemplate.opsForZSet().remove(RedisConstants.BLOG_MY_LIKED_KEY + userId, id.toString());
        }
    }

    /**
     * 收藏/取消收藏博客：blog_favorite 表存在记录即已收藏，toggle 为删行/插行
     */
    @Override
    public Boolean favoriteBlog(Long blogId) {
        Long userId = UserHolder.getUser().getId();
        if (blogMapper.selectById(blogId) == null) {
            throw new BadRequestException("博客不存在");
        }
        LambdaQueryWrapper<BlogFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BlogFavorite::getUserId, userId).eq(BlogFavorite::getBlogId, blogId);
        if (blogFavoriteMapper.selectCount(wrapper) > 0) {
            blogFavoriteMapper.delete(wrapper);
            return false;
        }
        blogFavoriteMapper.insert(BlogFavorite.builder()
                .userId(userId).blogId(blogId).createTime(LocalDateTime.now()).build());
        return true;
    }

    @Override
    public Boolean isFavorite(Long blogId) {
        UserVO user = UserHolder.getUser();
        if (user == null) {
            return false;
        }
        return blogFavoriteMapper.selectCount(new LambdaQueryWrapper<BlogFavorite>()
                .eq(BlogFavorite::getUserId, user.getId())
                .eq(BlogFavorite::getBlogId, blogId)) > 0;
    }

    /**
     * 我收藏的博客：收藏表查自己的记录（收藏时间倒序），selectBlogsByIds 按传入顺序返回；
     * 已被作者删除的博客不在结果中，自然从列表消失
     */
    @Override
    public List<BlogVO> myFavoriteBlogs() {
        Long userId = UserHolder.getUser().getId();
        List<BlogFavorite> favorites = blogFavoriteMapper.selectList(new LambdaQueryWrapper<BlogFavorite>()
                .eq(BlogFavorite::getUserId, userId)
                .orderByDesc(BlogFavorite::getCreateTime));
        if (favorites.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> blogIds = favorites.stream().map(BlogFavorite::getBlogId).collect(Collectors.toList());
        return blogMapper.selectBlogsByIds(blogIds).stream()
                .map(blog -> {
                    BlogVO vo = BeanUtil.copyProperties(blog, BlogVO.class);
                    queryUserInfo(vo);
                    isBlogLiked(vo);
                    vo.setIsFavorite(true);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 我点赞过的博客：读反向索引 blog:my:liked:{userId}（ZSet 按点赞时间倒序）。
     * 注意：该索引从上线才开始记录，历史点赞不回溯
     */
    @Override
    public List<BlogVO> myLikedBlogs() {
        Long userId = UserHolder.getUser().getId();
        Set<String> blogIdStrs = stringRedisTemplate.opsForZSet()
                .reverseRange(RedisConstants.BLOG_MY_LIKED_KEY + userId, 0, -1);
        if (blogIdStrs == null || blogIdStrs.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> blogIds = blogIdStrs.stream().map(Long::valueOf).collect(Collectors.toList());
        return blogMapper.selectBlogsByIds(blogIds).stream()
                .map(blog -> {
                    BlogVO vo = BeanUtil.copyProperties(blog, BlogVO.class);
                    queryUserInfo(vo);
                    isBlogLiked(vo);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<BlogVO> queryUserBlogs(Long userId, Integer current, Integer pageSize) {
        LambdaQueryWrapper<Blog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Blog::getUserId, userId)
                .orderByDesc(Blog::getCreateTime);
        List<Blog> records = blogMapper.selectPage(new Page<>(current, pageSize), queryWrapper).getRecords();
        return records.stream()
                .map(blog -> {
                    BlogVO vo = BeanUtil.copyProperties(blog, BlogVO.class);
                    queryUserInfo(vo);
                    isBlogLiked(vo);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<BlogVO> queryHotBlog(Integer current, Integer pageSize) {
        LambdaQueryWrapper<Blog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Blog::getLiked);
        List<Blog> records = blogMapper.selectPage(new Page<>(current, pageSize), wrapper).getRecords();
        return records.stream()
                .map(blog -> {
                    BlogVO vo = BeanUtil.copyProperties(blog, BlogVO.class);
                    // 查询用户信息
                    queryUserInfo(vo);
                    // 设置当前帖子的点赞状态
                    isBlogLiked(vo);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public BlogVO queryBlogById(Long id) {
        Blog blog = blogMapper.selectById(id);
        if (blog == null) {
            return null;
        }
        BlogVO vo = BeanUtil.copyProperties(blog, BlogVO.class);
        // 查询用户信息
        queryUserInfo(vo);
        // 查询当前帖子是否被当前用户点赞过
        isBlogLiked(vo);
        // 查询当前用户是否收藏过该博客（详情页收藏按钮状态）
        vo.setIsFavorite(isFavorite(vo.getId()));
        return vo;
    }

    @Override
    public Result<List<UserVO>> queryBlogLikes(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        // 1. 解析 ID
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());

        // 2. 查询用户，并按点赞顺序返回
        Result<List<UserVO>> listResult = userClient.selectUsersByIdsOrdered(ids);
        if(listResult==null||listResult.getData()==null){
            return Result.success(Collections.emptyList());
        }
        List<UserVO> users =listResult.getData();
        return Result.success(users);
    }

    @Override
    public Result<ScrollResult> queryBlogOfFollow(Long max, Integer offset) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //查询当前用户的收件箱中的博客
        String key=RedisConstants.FEED_KEY+userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        // 3.非空判断
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.success();
        }
        // ========== 4. 解析数据：blogId、minTime（时间戳）、offset ==========
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //获取动态id
            ids.add(Long.valueOf(typedTuple.getValue()));
            //获取分数（时间戳），处理相同时间戳发布大量博客情况
            long time = typedTuple.getScore().longValue();
            if (time == minTime) {
                os++;          // 同一秒发布的第N条，偏移量+1
            } else {
                minTime = time;
                os = 1;        // 新的一秒，重置偏移量
            }
        }
        //如果本次最小时间戳与上次相同，累加offset，下次跳过
        os = minTime == max ? os : os + offset;
        //根据id查询博客
        List<BlogVO> blogList = blogMapper.selectBlogsByIds(ids).stream()
                .map(blog -> BeanUtil.copyProperties(blog, BlogVO.class))
                .collect(Collectors.toList());
        //判断当前用户是否对当前博客进行点赞
        for (BlogVO vo : blogList) {
            //设置博客用户信息
            queryUserInfo(vo);
            //判断当前用户是否点赞
            isBlogLiked(vo);
        }
        // 6.封装并返回
        ScrollResult r = new ScrollResult();
        r.setList(blogList);
        r.setOffset(os);
        r.setMinTime(minTime);

        return Result.success(r);
    }


    @Override
    public void updateBlog(BlogDTO dto) {
        Long userId = UserHolder.getUser().getId();
        Blog old = blogMapper.selectById(dto.getId());
        if (old == null) {
            throw new IllegalArgumentException("博客不存在");
        }
        if (!old.getUserId().equals(userId)) {
            throw new IllegalArgumentException("只能修改自己的博客");
        }
        // 只允许更新标题/内容/图片，userId、点赞数、评论数不允许被前端篡改
        old.setTitle(dto.getTitle());
        old.setImages(dto.getImages());
        old.setContent(dto.getContent());
        blogMapper.updateById(old);
    }

    @Override
    @Transactional
    public void deleteBlogs(List<Long> ids) {
        Long userId = UserHolder.getUser().getId();
        if (ids == null || ids.isEmpty()) {
            throw new BadRequestException("请选择要删除的博客");
        }
        List<Blog> blogs = blogMapper.selectBatchIds(ids);
        if (blogs.isEmpty()) {
            throw new BadRequestException("博客不存在");
        }
        // 只能删除自己的博客，混入他人博客时整体拒绝
        for (Blog blog : blogs) {
            if (!blog.getUserId().equals(userId)) {
                throw new BadRequestException("无权删除他人博客");
            }
        }
        // 联动删除该批博客的全部评论
        blogCommentsMapper.delete(new LambdaQueryWrapper<BlogComments>()
                .in(BlogComments::getBlogId, ids));
        // 联动删除收藏记录（已删博客不再出现在任何人的收藏列表）
        blogFavoriteMapper.delete(new LambdaQueryWrapper<BlogFavorite>()
                .in(BlogFavorite::getBlogId, ids));
        blogMapper.deleteBatchIds(ids);
        // 清理点赞集合 + 每个点赞用户的"我点赞"反向索引（Redis 中的数据，避免残留）
        for (Long id : ids) {
            String likedKey = RedisConstants.BLOG_LIKED_KEY + id;
            Set<String> likers = stringRedisTemplate.opsForZSet().range(likedKey, 0, -1);
            if (likers != null) {
                for (String liker : likers) {
                    stringRedisTemplate.opsForZSet()
                            .remove(RedisConstants.BLOG_MY_LIKED_KEY + liker, id.toString());
                }
            }
            stringRedisTemplate.delete(likedKey);
        }
        // 从粉丝收件箱移除（与发布时的推送对应），避免动态流出现已删除的博客
        List<Follow> fans = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowUserId, userId));
        String[] idStrs = ids.stream().map(String::valueOf).toArray(String[]::new);
        for (Follow fan : fans) {
            stringRedisTemplate.opsForZSet().remove(RedisConstants.FEED_KEY + fan.getUserId(), idStrs);
        }
    }

    private void isBlogLiked(BlogVO vo){
        UserVO user= UserHolder.getUser();
        if(user==null){
            //用户没有登录直接返回false
            vo.setIsLike(false);
            return;
        }
        //判断当前用户是否点赞过
        Long userId=user.getId();
        String key=RedisConstants.BLOG_LIKED_KEY+vo.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        vo.setIsLike(score!=null);

    }
    private void queryUserInfo(BlogVO vo) {
        Result<UserVO> userResult = userClient.getUserInfo(vo.getUserId());
        if(userResult!=null&&userResult.getData()!=null){
            UserVO data = userResult.getData();
            vo.setName(data.getNickName());
            vo.setAuthorImages(data.getImages());
        }
    }
}
