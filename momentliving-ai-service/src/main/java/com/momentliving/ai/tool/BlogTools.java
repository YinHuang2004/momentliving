package com.momentliving.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C 端博客工具（上游 blog-service）。
 */
@Component
public class BlogTools {

    private final ToolSupport support;

    public BlogTools(ToolSupport support) {
        this.support = support;
    }

    @Tool(name = "get_hot_blogs",
            description = "获取平台热门探店笔记列表（标题、店铺、内容摘要），回答\"有什么推荐内容/热门笔记\"类问题可用")
    public List<Map<String, Object>> getHotBlogs(
            @ToolParam(required = false, description = "返回条数（1-10，默认 5）") Integer limit) {
        try {
            int n = limit == null ? 5 : Math.max(1, Math.min(10, limit));
            JsonNode items = support.extractList(support.get("blog", "/blog/hot", Map.of("current", 1), null));
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode item : items) {
                if (out.size() >= n) {
                    break;
                }
                Map<String, Object> blog = new LinkedHashMap<>();
                blog.put("blogId", ToolSupport.num(item, "id"));
                blog.put("title", ToolSupport.str(item, "title", ""));
                blog.put("shopId", ToolSupport.num(item, "shopId"));
                blog.put("liked", ToolSupport.num(item, "liked"));
                blog.put("summary", ToolSupport.truncate(ToolSupport.str(item, "content", ""), 80));
                out.add(blog);
            }
            return out;
        } catch (Exception e) {
            return ToolSupport.errorList("笔记查询失败：" + e.getMessage());
        }
    }
}
