package com.momentliving.ai.service;

import com.momentliving.ai.common.BusinessException;
import com.momentliving.ai.common.UserIdentity;
import com.momentliving.ai.config.AiProperties;
import com.momentliving.ai.dto.KnowledgeDocVO;
import com.momentliving.ai.dto.KnowledgeSearchRequest;
import com.momentliving.ai.dto.KnowledgeUploadRequest;
import com.momentliving.ai.prompt.Prompts;
import com.momentliving.ai.rag.PgKnowledgeVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 知识库管理（仅平台管理员）：上传切片向量化入库 / 列表 / 删除 / 检索预览。
 *
 * <p>切片策略与 Python 版一致：按空行分段，段超 500 字再按句边界切；
 * 批量向量化失败时以 embedding=NULL 入库（关键词检索仍可命中，不阻断入库）。
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);
    private static final int MAX_CHUNK_CHARS = 500;
    private static final Pattern PARAGRAPH_SPLIT = Pattern.compile("\\n\\s*\\n|\\r\\n\\s*\\r\\n");
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[。！？!?；;\\n])");

    private final JdbcTemplate jdbc;
    private final EmbeddingModel embeddingModel;
    private final PgKnowledgeVectorStore vectorStore;
    private final AiProperties props;

    public KnowledgeService(JdbcTemplate jdbc, EmbeddingModel embeddingModel,
                            PgKnowledgeVectorStore vectorStore, AiProperties props) {
        this.jdbc = jdbc;
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.props = props;
    }

    /** 上传：校验 → 建 doc(status=0) → 切片向量化写 chunk → 置 status=1 返回 VO。 */
    public KnowledgeDocVO upload(UserIdentity identity, KnowledgeUploadRequest req) {
        identity.requireAdmin();
        String title = req.title() == null ? "" : req.title().strip();
        if (title.isEmpty() || title.length() > 200) {
            throw new BusinessException("标题长度需在 1-200 字之间");
        }
        String sourceType = req.sourceType() == null || req.sourceType().isBlank()
                ? "help" : req.sourceType().strip();
        if (sourceType.length() > 20) {
            throw new BusinessException("来源类型过长（最多 20 字）");
        }
        String content = req.content();
        if (content == null || content.isBlank()) {
            throw new BusinessException("文档内容不能为空");
        }

        Long docId = jdbc.queryForObject(
                "INSERT INTO ai_knowledge_doc(title, source_type, status) VALUES (?, ?, 0) RETURNING id",
                Long.class, title, sourceType);

        List<String> chunks = split(content);
        int chunkCount;
        try {
            List<float[]> vectors = embeddingModel.embed(chunks);
            for (int i = 0; i < chunks.size(); i++) {
                jdbc.update("INSERT INTO ai_knowledge_chunk(doc_id, content, embedding) VALUES (?, ?, ?::vector)",
                        docId, chunks.get(i), PgKnowledgeVectorStore.toVectorLiteral(vectors.get(i)));
            }
            chunkCount = chunks.size();
        } catch (Exception e) {
            // 向量化失败：embedding=NULL 入库，关键词检索兜底（与 Python 版一致）
            log.warn("知识向量化失败，以 embedding=NULL 入库，docId={}", docId, e);
            for (String chunk : chunks) {
                jdbc.update("INSERT INTO ai_knowledge_chunk(doc_id, content) VALUES (?, ?)", docId, chunk);
            }
            chunkCount = chunks.size();
        }
        jdbc.update("UPDATE ai_knowledge_doc SET status = 1, chunk_count = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                chunkCount, docId);
        return docVO(docId);
    }

    public List<KnowledgeDocVO> list(UserIdentity identity) {
        identity.requireAdmin();
        return jdbc.query("""
                        SELECT id, title, source_type, status, chunk_count, created_at, updated_at
                        FROM ai_knowledge_doc ORDER BY id DESC LIMIT 200
                        """,
                (rs, i) -> new KnowledgeDocVO(
                        rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5),
                        iso(rs.getTimestamp(6)), iso(rs.getTimestamp(7))));
    }

    public void delete(UserIdentity identity, long docId) {
        identity.requireAdmin();
        jdbc.update("DELETE FROM ai_knowledge_chunk WHERE doc_id = ?", docId);
        jdbc.update("DELETE FROM ai_knowledge_doc WHERE id = ?", docId);
    }

    /**
     * 检索预览（管理端调优：看某问题会命中哪些知识片段）。
     * ⚠️ 必须走真实向量检索（不能像早期 Python 版漏传 retriever 静默降级）。
     */
    public String searchPreview(UserIdentity identity, KnowledgeSearchRequest req) {
        identity.requireAdmin();
        String query = req == null || req.query() == null ? "" : req.query().strip();
        if (query.isEmpty()) {
            return "";
        }
        List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(props.ragTopK())
                .similarityThreshold(PgKnowledgeVectorStore.MIN_SCORE)
                .build());
        StringBuilder sb = new StringBuilder();
        for (Document doc : docs) {
            String text = doc.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            if (sb.length() + text.length() > props.knowledgeMaxChars()) {
                break;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(text);
        }
        return sb.toString().strip();
    }

    /** 切片：空行分段 → 段超 500 字按句边界切。 */
    private List<String> split(String content) {
        List<String> chunks = new ArrayList<>();
        for (String paragraph : PARAGRAPH_SPLIT.split(content)) {
            String p = paragraph.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() <= MAX_CHUNK_CHARS) {
                chunks.add(p);
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String sentence : SENTENCE_BOUNDARY.split(p)) {
                if (current.length() + sentence.length() > MAX_CHUNK_CHARS && current.length() > 0) {
                    chunks.add(current.toString().strip());
                    current.setLength(0);
                }
                current.append(sentence);
            }
            if (!current.isEmpty()) {
                chunks.add(current.toString().strip());
            }
        }
        return chunks;
    }

    private KnowledgeDocVO docVO(long docId) {
        return jdbc.queryForObject("""
                        SELECT id, title, source_type, status, chunk_count, created_at, updated_at
                        FROM ai_knowledge_doc WHERE id = ?
                        """,
                (rs, i) -> new KnowledgeDocVO(
                        rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5),
                        iso(rs.getTimestamp(6)), iso(rs.getTimestamp(7))), docId);
    }

    private static String iso(Timestamp ts) {
        LocalDateTime t = ts == null ? LocalDateTime.now() : ts.toLocalDateTime();
        return ConversationService.iso(t);
    }
}
