package com.momentliving.ai.controller;

import com.momentliving.ai.common.BusinessException;
import com.momentliving.ai.common.UserIdentity;
import com.momentliving.ai.dto.FeedbackRequest;
import com.momentliving.result.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 消息反馈接口：目标消息必须是 assistant 角色，且其会话属于当前身份。
 */
@RestController
@RequestMapping("/ai")
public class FeedbackController {

    private final JdbcTemplate jdbc;

    public FeedbackController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 反馈落库：校验评分 1-5 与评论长度，消息归属校验通过后写 ai_feedback。 */
    @PostMapping("/feedback")
    public Result<Void> feedback(
            @RequestHeader(value = UserIdentity.HEADER_USER, required = false) String user,
            @RequestHeader(value = UserIdentity.HEADER_MERCHANT, required = false) String merchant,
            @RequestBody(required = false) FeedbackRequest req) {
        UserIdentity identity = UserIdentity.of(user, merchant, null).requireChatUser();
        long uid = identity.chatUserId();
        int userType = identity.chatUserType();
        if (req == null || req.messageId() == null) {
            throw new BusinessException("messageId 不能为空");
        }
        Integer rating = req.rating();
        if (rating == null || rating < 1 || rating > 5) {
            throw new BusinessException("评分需在 1-5 之间");
        }
        String comment = req.comment();
        if (comment != null && comment.length() > 500) {
            throw new BusinessException("评论过长（最多 500 字）");
        }
        // 消息存在 + role=assistant + 会话归属校验（一条 SQL 同时完成）
        List<Long> ids = jdbc.query("""
                        SELECT m.id FROM ai_message m
                        JOIN ai_conversation c ON m.conversation_id = c.id
                        WHERE m.id = ? AND m.role = 'assistant'
                          AND c.user_id = ? AND c.user_type = ?
                        """,
                (rs, i) -> rs.getLong(1), req.messageId(), uid, userType);
        if (ids.isEmpty()) {
            throw new BusinessException("消息不存在");
        }
        jdbc.update("INSERT INTO ai_feedback(message_id, user_id, rating, comment) VALUES (?, ?, ?, ?)",
                req.messageId(), uid, rating, comment);
        return Result.success();
    }
}
