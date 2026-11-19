package com.momentliving.ai.memory;

import com.momentliving.ai.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 混合会话存储（实现 Spring AI {@link ChatMemory} SPI）：
 * <ul>
 *   <li><b>热路径</b>：Redis List 滑动窗口（key = ai:conv:{id}，保留最近 windowSize 条，
 *       TTL 7 天）——多轮对话毫秒级读取；</li>
 *   <li><b>冷路径</b>：PG ai_message 全量落库——Redis miss/故障时按 id 倒序取最近 N 条回填；</li>
 *   <li><b>降级</b>：Redis 任何异常只打 warning 不抛出，PG 单独可用（生产韧性设计）。</li>
 * </ul>
 *
 * <p>存储格式与 Python 版（LangChain 标准格式）一致，两套实现可共用同一个 Redis 实例灰度。
 */
@Component
public class HybridChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(HybridChatMemory.class);
    /** 与 Python 版 redis key 完全一致 */
    private static final String KEY_PREFIX = "ai:conv:";
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final AiProperties props;

    public HybridChatMemory(StringRedisTemplate redis, JdbcTemplate jdbc, AiProperties props) {
        this.redis = redis;
        this.jdbc = jdbc;
        this.props = props;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        long convId = Long.parseLong(conversationId);
        List<String> encoded = messages.stream().map(ChatMessageCodec::encode).toList();
        // 1. Redis 热窗口（失败降级 PG）
        try {
            String key = KEY_PREFIX + conversationId;
            redis.opsForList().rightPushAll(key, encoded);
            redis.opsForList().trim(key, -props.windowSize(), -1);
            redis.expire(key, TTL);
        } catch (Exception e) {
            log.warn("Redis 会话窗口写入失败（降级为仅 PG 存储），conversationId={}", conversationId, e);
        }
        // 2. PG 全量落库（role: user / assistant）
        List<Message> persistable = messages.stream()
                .filter(m -> m instanceof UserMessage || m instanceof AssistantMessage)
                .toList();
        if (persistable.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(
                "INSERT INTO ai_message(conversation_id, role, content) VALUES (?, ?, ?)",
                persistable, persistable.size(), (ps, msg) -> {
                    ps.setLong(1, convId);
                    ps.setString(2, msg instanceof UserMessage ? "user" : "assistant");
                    ps.setString(3, msg.getText() == null ? "" : msg.getText());
                });
    }

    @Override
    public List<Message> get(String conversationId) {
        // 1. Redis 命中直接返回
        try {
            List<String> raw = redis.opsForList().range(KEY_PREFIX + conversationId, 0, -1);
            if (raw != null && !raw.isEmpty()) {
                return raw.stream()
                        .map(ChatMessageCodec::decode)
                        .filter(Objects::nonNull)
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Redis 会话窗口读取失败（降级 PG），conversationId={}", conversationId, e);
        }
        // 2. PG 冷路径：最近 windowSize 条，按 id ASC 返回
        List<Message> messages = new ArrayList<>();
        jdbc.query("""
                        SELECT id, role, content FROM (
                            SELECT id, role, content FROM ai_message
                            WHERE conversation_id = ? AND role IN ('user', 'assistant')
                            ORDER BY id DESC LIMIT ?
                        ) t ORDER BY t.id ASC
                        """,
                ps -> {
                    ps.setLong(1, Long.parseLong(conversationId));
                    ps.setInt(2, props.windowSize());
                },
                rs -> {
                    String role = rs.getString(2);
                    String content = rs.getString(3);
                    messages.add("user".equals(role) ? new UserMessage(content) : new AssistantMessage(content));
                });
        // 3. 回填 Redis 热窗口（尽力而为）
        if (!messages.isEmpty()) {
            try {
                String key = KEY_PREFIX + conversationId;
                redis.opsForList().rightPushAll(key, messages.stream().map(ChatMessageCodec::encode).toList());
                redis.opsForList().trim(key, -props.windowSize(), -1);
                redis.expire(key, TTL);
            } catch (Exception e) {
                log.warn("Redis 会话窗口回填失败，conversationId={}", conversationId, e);
            }
        }
        return messages;
    }

    @Override
    public void clear(String conversationId) {
        try {
            redis.delete(KEY_PREFIX + conversationId);
        } catch (Exception e) {
            log.warn("Redis 会话窗口删除失败，conversationId={}", conversationId, e);
        }
        jdbc.update("DELETE FROM ai_message WHERE conversation_id = ?", Long.parseLong(conversationId));
    }
}
