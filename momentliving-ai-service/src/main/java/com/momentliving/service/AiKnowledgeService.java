package com.momentliving.service;

import com.momentliving.entity.AiKnowledgeDoc;

import java.util.List;

/**
 * AI 知识库（轻量 RAG）：文档上传 → 段落切分 → 向量化 → 相似度/关键词检索
 */
public interface AiKnowledgeService {

    /** 上传/登记文档（纯文本或 Markdown 内容），同步切分并向量化 */
    AiKnowledgeDoc upload(String title, String sourceType, String content);

    /** 文档列表（管理端） */
    List<AiKnowledgeDoc> listDocs();

    /** 删除文档及其知识块（管理端） */
    void deleteDoc(Long docId);

    /**
     * 检索与问题相关的知识片段（拼接为可拼入 System Prompt 的文本，无结果返回 ""）
     * 优先向量余弦相似度 top-k；无向量数据时降级关键词匹配
     */
    String retrieveContext(String query, int topK, int maxChars);
}
