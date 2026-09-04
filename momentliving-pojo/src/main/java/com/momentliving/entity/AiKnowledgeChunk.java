package com.momentliving.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 知识块表（ai_knowledge_chunk）：文档切分后的片段。
 *
 * <p>轻量 RAG 方案：向量以 JSON 数组文本存在 embedding 列，检索时载入内存做余弦相似度
 * top-k（知识库规模 < 1 万块完全够用），不依赖 Redis Stack / PGVector。
 * embedding 为空时降级为关键词 LIKE 检索。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_knowledge_chunk")
public class AiKnowledgeChunk implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    /** 切分后的文本片段 */
    private String content;
    /** 向量（JSON 数组文本，如 [0.12,-0.33,...]；未向量化时为 null） */
    private String embedding;
    private LocalDateTime createdAt;
}
