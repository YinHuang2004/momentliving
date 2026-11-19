package com.momentliving.ai.dto;

/**
 * 知识库检索测试请求（管理端调优：看某问题会命中哪些知识片段）。
 */
public record KnowledgeSearchRequest(String query) {
}
