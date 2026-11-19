package com.momentliving.ai.dto;

/**
 * 知识库上传请求（管理端）。
 */
public record KnowledgeUploadRequest(
        String title,
        String sourceType,
        String content) {
}
