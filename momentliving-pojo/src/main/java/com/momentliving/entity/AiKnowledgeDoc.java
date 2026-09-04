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
 * AI 知识库文档表（ai_knowledge_doc）：文档元数据；切分后的块与向量在 ai_knowledge_chunk
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_knowledge_doc")
public class AiKnowledgeDoc implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 文档标题 */
    private String title;
    /** 来源类型：faq=常见问题 / help=帮助文档 / rule=平台规则 */
    private String sourceType;
    /** 原始文件 URL（OSS，可空：直接粘贴文本时为 null） */
    private String fileUrl;
    /** 状态：0=处理中 1=已入库 2=失败 */
    private Integer status;
    /** 切分块数 */
    private Integer chunkCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
