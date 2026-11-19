package com.momentliving.ai.service;

import com.momentliving.ai.common.BusinessException;
import com.momentliving.ai.common.UserIdentity;
import com.momentliving.ai.dto.ConversationVO;
import com.momentliving.ai.dto.MessageVO;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 会话管理：归属校验（user_id + user_type 双匹配，防横向越权）、CRUD、触活。
 */
@Service
public class ConversationService {

    private final JdbcTemplate jdbc;
    private final ChatMemory chatMemory;

    public ConversationService(JdbcTemplate jdbc, ChatMemory chatMemory) {
        this.jdbc = jdbc;
        this.chatMemory = chatMemory;
    }

    /** ai_conversation 行。 */
    public record Row(long id, long userId, int userType, String title,
                      String lastMessage, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /** 无 conversationId 则新建（title=新对话），有则做所有权校验。 */
    public Row getOrCreate(UserIdentity identity, Long conversationId) {
        long uid = identity.chatUserId();
        int userType = identity.chatUserType();
        if (conversationId == null) {
            return jdbc.queryForObject("""
                            INSERT INTO ai_conversation(user_id, user_type) VALUES (?, ?)
                            RETURNING id, user_id, user_type, title, last_message, created_at, updated_at
                            """,
                    (rs, i) -> mapRow(rs), uid, userType);
        }
        List<Row> rows = jdbc.query("""
                        SELECT id, user_id, user_type, title, last_message, created_at, updated_at
                        FROM ai_conversation
                        WHERE id = ? AND user_id = ? AND user_type = ?
                        """,
                (rs, i) -> mapRow(rs), conversationId, uid, userType);
        if (rows.isEmpty()) {
            throw new BusinessException("无权访问该会话");
        }
        return rows.get(0);
    }

    /** 对话完成后触活：last_message 截 200 字 + 刷新 updated_at。 */
    public void touch(long conversationId, String lastMessage) {
        String brief = lastMessage == null ? "" : lastMessage.substring(0, Math.min(200, lastMessage.length()));
        jdbc.update("UPDATE ai_conversation SET last_message = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                brief, conversationId);
    }

    /** 会话列表（最近 50 个，按 updated_at 倒序）。 */
    public List<ConversationVO> list(UserIdentity identity) {
        long uid = identity.chatUserId();
        int userType = identity.chatUserType();
        return jdbc.query("""
                        SELECT id, title, last_message, updated_at FROM ai_conversation
                        WHERE user_id = ? AND user_type = ?
                        ORDER BY updated_at DESC LIMIT 50
                        """,
                (rs, i) -> new ConversationVO(
                        rs.getLong("id"), null, null,
                        rs.getString("title"), rs.getString("last_message"),
                        null, iso(toLocal(rs.getTimestamp("updated_at")))),
                uid, userType);
    }

    /** 会话内消息历史（按 id 升序，不含 system）。 */
    public List<MessageVO> messages(UserIdentity identity, long conversationId) {
        getOrCreate(identity, conversationId);   // 归属校验
        return jdbc.query("""
                        SELECT id, conversation_id, role, content, created_at
                        FROM ai_message
                        WHERE conversation_id = ? AND role IN ('user', 'assistant')
                        ORDER BY id ASC
                        """,
                (rs, i) -> new MessageVO(
                        rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        null, iso(toLocal(rs.getTimestamp(5)))),
                conversationId);
    }

    /** 删除会话：连删 PG 消息 + Redis 窗口。 */
    public void delete(UserIdentity identity, long conversationId) {
        getOrCreate(identity, conversationId);   // 归属校验
        jdbc.update("DELETE FROM ai_message WHERE conversation_id = ?", conversationId);
        jdbc.update("DELETE FROM ai_conversation WHERE id = ?", conversationId);
        chatMemory.clear(String.valueOf(conversationId));
    }

    private Row mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row(
                rs.getLong(1), rs.getLong(2), rs.getInt(3),
                rs.getString(4), rs.getString(5),
                toLocal(rs.getTimestamp(6)), toLocal(rs.getTimestamp(7)));
    }

    private static LocalDateTime toLocal(Timestamp ts) {
        return ts == null ? LocalDateTime.now() : ts.toLocalDateTime();
    }

    /** ISO 秒级字符串（与 Python isoformat(timespec="seconds") 对齐）。 */
    public static String iso(LocalDateTime t) {
        return t == null ? null : t.truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
