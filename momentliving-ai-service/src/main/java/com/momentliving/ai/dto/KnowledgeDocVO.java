package com.momentliving.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 知识库文档 VO（管理端）。
 * status：0=处理中 1=已入库 2=失败。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KnowledgeDocVO(
        long id,
        String title,
        String sourceType,
        Integer status,
        int chunkCount,
        String createdAt,
        String updatedAt) {
}
