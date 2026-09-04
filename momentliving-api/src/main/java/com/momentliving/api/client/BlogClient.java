package com.momentliving.api.client;

import com.momentliving.api.config.FeignConfig;
import com.momentliving.result.Result;
import com.momentliving.vo.BlogVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * blog-service 内部接口客户端（🆕 ai-service Function Calling 用）
 * 复用现有公开只读接口：/blog/hot 热门博客、/blog/{id} 博客详情
 */
@FeignClient(name = "blog-service", configuration = FeignConfig.class)
public interface BlogClient {

    /** 热门探店博客列表（按点赞数排序） */
    @GetMapping("/blog/hot")
    Result<List<BlogVO>> queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current);

    /** 博客详情（含店铺名/作者昵称头像回填） */
    @GetMapping("/blog/{id}")
    Result<BlogVO> queryBlogById(@PathVariable("id") Long id);
}
