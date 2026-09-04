package com.momentliving.controller;

import com.momentliving.context.AdminHolder;
import com.momentliving.entity.AiKnowledgeDoc;
import com.momentliving.exception.BadRequestException;
import com.momentliving.result.Result;
import com.momentliving.service.AiKnowledgeService;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 知识库管理（仅平台管理员：网关管理端模式透传 X-Admin-Id，这里校验 AdminHolder）
 */
@RestController
@RequestMapping("/ai/knowledge")
public class AiKnowledgeController {

    @Resource
    private AiKnowledgeService knowledgeService;

    /** 上传文档（纯文本/Markdown 内容；文件类内容先读成文本再传） */
    @PostMapping("/upload")
    public Result<AiKnowledgeDoc> upload(@RequestBody KnowledgeUploadDTO dto) {
        checkAdmin();
        return Result.success(knowledgeService.upload(dto.getTitle(), dto.getSourceType(), dto.getContent()));
    }

    /** 文档列表 */
    @GetMapping("/list")
    public Result<List<AiKnowledgeDoc>> list() {
        checkAdmin();
        return Result.success(knowledgeService.listDocs());
    }

    /** 删除文档（连同知识块） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        checkAdmin();
        knowledgeService.deleteDoc(id);
        return Result.success();
    }

    /** 检索测试（管理端调优知识库用：看某问题会命中哪些知识片段） */
    @PostMapping("/search")
    public Result<String> search(@RequestBody Map<String, String> body) {
        checkAdmin();
        String query = body.getOrDefault("query", "");
        return Result.success(knowledgeService.retrieveContext(query, 5, 1500));
    }

    private void checkAdmin() {
        if (AdminHolder.getAdmin() == null) {
            throw new BadRequestException("仅平台管理员可管理知识库");
        }
    }

    /** 上传入参 */
    @Data
    public static class KnowledgeUploadDTO {
        /** 文档标题 */
        private String title;
        /** 来源类型：faq / help / rule */
        private String sourceType;
        /** 文档内容（纯文本或 Markdown） */
        private String content;
    }
}
