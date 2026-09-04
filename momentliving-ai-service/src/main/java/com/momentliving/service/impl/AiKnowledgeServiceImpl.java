package com.momentliving.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.momentliving.config.AiProperties;
import com.momentliving.entity.AiKnowledgeChunk;
import com.momentliving.entity.AiKnowledgeDoc;
import com.momentliving.exception.BadRequestException;
import com.momentliving.mapper.AiKnowledgeChunkMapper;
import com.momentliving.mapper.AiKnowledgeDocMapper;
import com.momentliving.service.AiKnowledgeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 轻量 RAG 实现（不依赖 Redis Stack / PGVector）：
 * - 切分：按空行分段，段超 500 字再按句切；
 * - 向量：走大模型 embedding 接口，向量以 JSON 文本存 MySQL（ai_knowledge_chunk.embedding）；
 * - 检索：向量非空 → 内存余弦相似度 top-k；否则降级关键词 LIKE 匹配；
 * - 全库 < 1 万块时内存检索耗时可忽略；知识库规模上来后再升级 Redis Stack。
 */
@Slf4j
@Service
public class AiKnowledgeServiceImpl implements AiKnowledgeService {

    private static final int MAX_CHUNK_CHARS = 500;

    @Resource
    private AiKnowledgeDocMapper docMapper;

    @Resource
    private AiKnowledgeChunkMapper chunkMapper;

    @Resource
    private AiProperties aiProperties;

    @Resource
    private EmbeddingModel embeddingModel;

    @Override
    public AiKnowledgeDoc upload(String title, String sourceType, String content) {
        if (StrUtil.isBlank(title) || StrUtil.isBlank(content)) {
            throw new BadRequestException("文档标题与内容不能为空");
        }
        AiKnowledgeDoc doc = AiKnowledgeDoc.builder()
                .title(title.trim())
                .sourceType(StrUtil.isBlank(sourceType) ? "help" : sourceType)
                .status(0) // 处理中
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        docMapper.insert(doc);

        try {
            List<String> chunks = split(content);
            for (String chunk : chunks) {
                String embedding = tryEmbed(chunk);
                AiKnowledgeChunk entity = AiKnowledgeChunk.builder()
                        .docId(doc.getId())
                        .content(chunk)
                        .embedding(embedding)
                        .createdAt(LocalDateTime.now())
                        .build();
                chunkMapper.insert(entity);
            }
            doc.setStatus(1); // 已入库
            doc.setChunkCount(chunks.size());
            log.info("知识库文档入库成功 docId={}, title={}, chunks={}", doc.getId(), doc.getTitle(), chunks.size());
        } catch (Exception e) {
            doc.setStatus(2); // 失败（知识块已尽力保存，关键词检索仍可用）
            log.error("知识库文档入库异常 docId={}", doc.getId(), e);
        }
        doc.setUpdatedAt(LocalDateTime.now());
        docMapper.updateById(doc);
        return doc;
    }

    @Override
    public List<AiKnowledgeDoc> listDocs() {
        return docMapper.selectList(new LambdaQueryWrapper<AiKnowledgeDoc>()
                .orderByDesc(AiKnowledgeDoc::getId)
                .last("limit 200"));
    }

    @Override
    public void deleteDoc(Long docId) {
        AiKnowledgeDoc doc = docMapper.selectById(docId);
        if (doc == null) {
            throw new BadRequestException("文档不存在");
        }
        docMapper.deleteById(docId);
        chunkMapper.delete(new LambdaQueryWrapper<AiKnowledgeChunk>().eq(AiKnowledgeChunk::getDocId, docId));
    }

    @Override
    public String retrieveContext(String query, int topK, int maxChars) {
        if (StrUtil.isBlank(query)) {
            return "";
        }
        List<AiKnowledgeChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<AiKnowledgeChunk>()
                .isNotNull(AiKnowledgeChunk::getContent)
                .last("limit 5000")); // 防御性上限，禁止全表捞
        if (chunks.isEmpty()) {
            return "";
        }

        List<AiKnowledgeChunk> matched;
        try {
            matched = retrieveByVector(query, chunks, topK);
        } catch (Exception e) {
            log.warn("向量检索失败，降级关键词匹配：{}", e.getMessage());
            matched = retrieveByKeyword(query, chunks, topK);
        }

        StringBuilder sb = new StringBuilder();
        for (AiKnowledgeChunk chunk : matched) {
            if (sb.length() + chunk.getContent().length() > maxChars) {
                break;
            }
            sb.append(chunk.getContent()).append("\n");
        }
        return sb.toString().trim();
    }

    /** 余弦相似度 top-k */
    private List<AiKnowledgeChunk> retrieveByVector(String query, List<AiKnowledgeChunk> chunks, int topK) {
        float[] queryVector = embeddingModel.embed(query);
        record Scored(AiKnowledgeChunk chunk, double score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (AiKnowledgeChunk chunk : chunks) {
            float[] vector = parseVector(chunk.getEmbedding());
            if (vector == null || vector.length != queryVector.length) {
                continue;
            }
            scored.add(new Scored(chunk, cosine(queryVector, vector)));
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(topK)
                .filter(s -> s.score() > 0.2) // 相似度过低的丢弃，避免硬塞无关知识
                .map(Scored::chunk)
                .toList();
    }

    /** 关键词降级：按 2 字滑窗切词，命中越多越靠前 */
    private List<AiKnowledgeChunk> retrieveByKeyword(String query, List<AiKnowledgeChunk> chunks, int topK) {
        List<String> terms = slideTerms(query);
        return chunks.stream()
                .filter(c -> terms.stream().anyMatch(t -> c.getContent().contains(t)))
                .sorted(Comparator.comparingLong(
                                (AiKnowledgeChunk c) -> terms.stream().filter(t -> c.getContent().contains(t)).count())
                        .reversed())
                .limit(topK)
                .toList();
    }

    /** 尝试向量化：失败返回 null（关键词检索兜底），不阻断入库 */
    private String tryEmbed(String chunk) {
        try {
            float[] vector = embeddingModel.embed(chunk);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < vector.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(vector[i]);
            }
            return sb.append(']').toString();
        } catch (Exception e) {
            log.warn("向量化失败（该块将走关键词检索）：{}", e.getMessage());
            return null;
        }
    }

    /** 按空行分段 + 超长再切 */
    static List<String> split(String content) {
        List<String> result = new ArrayList<>();
        Arrays.stream(content.split("\\n\\s*\\n|\\r\\n\\s*\\r\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(paragraph -> {
                    if (paragraph.length() <= MAX_CHUNK_CHARS) {
                        result.add(paragraph);
                        return;
                    }
                    // 中文按句切
                    StringBuilder current = new StringBuilder();
                    for (String sentence : paragraph.split("(?<=[。！？!?；;\n])")) {
                        if (current.length() + sentence.length() > MAX_CHUNK_CHARS && current.length() > 0) {
                            result.add(current.toString().trim());
                            current.setLength(0);
                        }
                        current.append(sentence);
                    }
                    if (current.length() > 0) {
                        result.add(current.toString().trim());
                    }
                });
        return result.isEmpty() ? List.of(content.trim()) : result;
    }

    private static List<String> slideTerms(String query) {
        List<String> terms = new ArrayList<>();
        String q = query.replaceAll("[\\s，。？！,\\?!]", "");
        for (int i = 0; i + 2 <= q.length(); i++) {
            terms.add(q.substring(i, i + 2));
        }
        return terms;
    }

    private static float[] parseVector(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            String[] parts = json.replace("[", "").replace("]", "").split(",");
            float[] vector = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                vector[i] = Float.parseFloat(parts[i].trim());
            }
            return vector;
        } catch (Exception e) {
            return null;
        }
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
