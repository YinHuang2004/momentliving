package com.momentliving.tools;

import com.momentliving.api.client.BlogClient;
import com.momentliving.result.Result;
import com.momentliving.vo.BlogVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 博客查询工具（Function Calling）：热门探店笔记，用于推荐场景补充"大家都在玩什么"
 */
@Slf4j
@Component
public class BlogTools {

    @Resource
    private BlogClient blogClient;

    @Tool(description = "获取平台热门探店笔记列表（标题、店铺、内容摘要）")
    public List<Map<String, Object>> getHotBlogs(
            @ToolParam(description = "返回数量，默认5，最大10", required = false) Integer limit) {
        long start = System.currentTimeMillis();
        Result<List<BlogVO>> result = blogClient.queryHotBlog(1);
        int max = limit == null ? 5 : Math.min(limit, 10);
        List<Map<String, Object>> data = (result.getData() == null ? List.<BlogVO>of() : result.getData())
                .stream()
                .limit(max)
                .map(b -> Map.<String, Object>of(
                        "blogId", b.getId(),
                        "title", b.getTitle() == null ? "" : b.getTitle(),
                        "shopId", b.getShopId() == null ? 0 : b.getShopId(),
                        "liked", b.getLiked() == null ? 0 : b.getLiked(),
                        "summary", abbreviate(b.getContent(), 80)))
                .toList();
        log.info("AI工具调用 name=getHotBlogs resultSize={} costMs={}", data.size(), System.currentTimeMillis() - start);
        return data;
    }

    private String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
