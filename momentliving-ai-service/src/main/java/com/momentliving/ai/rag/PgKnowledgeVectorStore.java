package com.momentliving.ai.rag;

import com.momentliving.ai.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 自研 pgvector {@link VectorStore}：直接承载既有 ai_knowledge_chunk 表
 * （embedding vector(1024)，HNSW + vector_cosine_ops 索引），无需 pgvector starter 的
 * 默认 vector_store 表结构，历史数据零迁移。
 *
 * <p>检索语义与 Python 版 pgvector_retriever.py 对齐：
 * <ul>
 *   <li>余弦距离操作符 {@code <=>}，score = 1 - 余弦距离，阈值 score &gt; 0.2；</li>
 *   <li>只检索 status=1（已入库）文档；</li>
 *   <li>向量检索异常时降级 <b>2-gram 关键词检索</b>（embedding 为 NULL 的 chunk 仍可命中）。</li>
 * </ul>
 */
@Component
public class PgKnowledgeVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(PgKnowledgeVectorStore.class);
    /** 相似度阈值（与 Python 版 MIN_SCORE 一致） */
    public static final double MIN_SCORE = 0.2;

    private static final Pattern CLEAN_PATTERN = Pattern.compile("[\\s，。？！,\\?!]");
    private static final int KEYWORD_SCAN_LIMIT = 5000;

    private final JdbcTemplate jdbc;
    private final EmbeddingModel embeddingModel;

    public PgKnowledgeVectorStore(JdbcTemplate jdbc, EmbeddingModel embeddingModel) {
        this.jdbc = jdbc;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public String getName() {
        return "PgKnowledgeVectorStore";
    }

    /** 切片 Document 向量化后写入 ai_knowledge_chunk（metadata 必须带 docId）。 */
    @Override
    public void add(List<Document> documents) {
        for (Document doc : documents) {
            Object docId = doc.getMetadata().get("docId");
            if (docId == null) {
                throw new IllegalArgumentException("Document metadata 缺少 docId");
            }
            float[] vector = embeddingModel.embed(doc);
            jdbc.update("INSERT INTO ai_knowledge_chunk(doc_id, content, embedding) VALUES (?, ?, ?::vector)",
                    ((Number) docId).longValue(), doc.getText(), toVectorLiteral(vector));
        }
    }

    @Override
    public void delete(List<String> idList) {
        jdbc.batchUpdate("DELETE FROM ai_knowledge_chunk WHERE id = ?",
                idList, idList.size(), (ps, id) -> ps.setLong(1, Long.parseLong(id)));
    }

    @Override
    public void delete(Filter.Expression filter) {
        throw new UnsupportedOperationException("本实现不支持 Filter.Expression 删除");
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        try {
            List<Document> result = vectorSearch(request);
            if (!result.isEmpty()) {
                return result;
            }
            // 向量检索零命中也降级关键词兜底一次（冷启动/生僻词场景）
            return keywordSearch(request);
        } catch (Exception e) {
            log.warn("pgvector 向量检索失败，降级 2-gram 关键词检索", e);
            return keywordSearch(request);
        }
    }

    /** 向量检索：pgvector 余弦距离 <=>，score = 1 - distance。 */
    private List<Document> vectorSearch(SearchRequest request) {
        float[] vector = embeddingModel.embed(request.getQuery());
        String literal = toVectorLiteral(vector);
        double maxDistance = 1 - request.getSimilarityThreshold();
        return jdbc.query("""
                        SELECT c.id, c.content, 1 - (c.embedding <=> ?::vector) AS score
                        FROM ai_knowledge_chunk c
                        JOIN ai_knowledge_doc d ON c.doc_id = d.id
                        WHERE d.status = 1
                          AND c.content IS NOT NULL
                          AND (c.embedding <=> ?::vector) < ?
                        ORDER BY c.embedding <=> ?::vector
                        LIMIT ?
                        """,
                ps -> {
                    ps.setString(1, literal);
                    ps.setString(2, literal);
                    ps.setDouble(3, maxDistance);
                    ps.setString(4, literal);
                    ps.setInt(5, request.getTopK());
                },
                (rs, i) -> {
                    Map<String, Object> meta = new HashMap<>(2);
                    meta.put("chunkId", rs.getLong(1));
                    meta.put("score", rs.getDouble(3));
                    return Document.builder()
                            .id(String.valueOf(rs.getLong(1)))
                            .text(rs.getString(2))
                            .metadata(meta)
                            .score(rs.getDouble(3))
                            .build();
                });
    }

    /**
     * 关键词降级检索：清洗后做 2 字滑窗切词，统计每个 chunk 的 2-gram 命中数排序。
     * （与 Python 版 _retrieve_by_keyword 同款算法）
     */
    private List<Document> keywordSearch(SearchRequest request) {
        String cleaned = CLEAN_PATTERN.matcher(request.getQuery()).replaceAll("");
        if (cleaned.isEmpty()) {
            return List.of();
        }
        List<String> grams = new ArrayList<>();
        for (int i = 0; i + 2 <= cleaned.length(); i += 2) {
            grams.add(cleaned.substring(i, i + 2));
        }
        if (grams.isEmpty()) {
            grams.add(cleaned);
        }
        List<Object[]> rows = jdbc.query(
                """
                        SELECT c.id, c.content
                        FROM ai_knowledge_chunk c
                        JOIN ai_knowledge_doc d ON c.doc_id = d.id
                        WHERE d.status = 1 AND c.content IS NOT NULL
                        LIMIT %d
                        """.formatted(KEYWORD_SCAN_LIMIT),
                (rs, i) -> new Object[]{rs.getLong(1), rs.getString(2)});
        record Hit(long id, String content, int hits) {}
        List<Hit> hits = rows.stream()
                .map(r -> {
                    String content = (String) r[1];
                    int count = 0;
                    for (String gram : grams) {
                        if (content.contains(gram)) {
                            count++;
                        }
                    }
                    return count > 0 ? new Hit((Long) r[0], content, count) : null;
                })
                .filter(h -> h != null)
                .sorted(Comparator.comparingInt(Hit::hits).reversed())
                .limit(request.getTopK())
                .toList();
        return hits.stream()
                .map(h -> Document.builder()
                        .id(String.valueOf(h.id()))
                        .text(h.content())
                        .metadata(Map.of("score", (double) h.hits() / grams.size()))
                        .score((double) h.hits() / grams.size())
                        .build())
                .collect(Collectors.toList());
    }

    /** float[] → pgvector 字面量（"[0.1,0.2,..."），与 Python 版格式一致。 */
    public static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
