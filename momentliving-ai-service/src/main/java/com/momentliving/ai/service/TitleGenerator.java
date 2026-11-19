package com.momentliving.ai.service;

import com.momentliving.ai.prompt.Prompts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 会话标题异步生成（fire-and-forget，对应 Python 版 spawn_title_task）。
 *
 * <p>只有 title 仍为「新对话」才生成（条件更新防覆盖用户改名）；
 * 失败回退为用户消息前 12 字。
 */
@Component
public class TitleGenerator {

    private static final Logger log = LoggerFactory.getLogger(TitleGenerator.class);
    private static final String DEFAULT_TITLE = "新对话";

    private final ChatModel chatModel;
    private final JdbcTemplate jdbc;

    public TitleGenerator(ChatModel chatModel, JdbcTemplate jdbc) {
        this.chatModel = chatModel;
        this.jdbc = jdbc;
    }

    @Async
    public void generate(long conversationId, String firstUserMessage) {
        String fallback = firstUserMessage.length() > 12
                ? firstUserMessage.substring(0, 12) : firstUserMessage;
        try {
            String title = chatModel.call(Prompts.GENERATE_TITLE.formatted(firstUserMessage));
            title = title == null ? "" : title.replaceAll("['\"。！!？?\\n]", "").trim();
            if (title.isEmpty()) {
                title = fallback;
            }
            if (title.length() > 20) {
                title = title.substring(0, 20);
            }
            jdbc.update("UPDATE ai_conversation SET title = ? WHERE id = ? AND title = ?",
                    title, conversationId, DEFAULT_TITLE);
        } catch (Exception e) {
            log.warn("会话标题生成失败（回退默认），conversationId={}", conversationId, e);
            try {
                jdbc.update("UPDATE ai_conversation SET title = ? WHERE id = ? AND title = ?",
                        fallback, conversationId, DEFAULT_TITLE);
            } catch (Exception ignore) {
                // 兜底也失败就放弃，标题保持「新对话」
            }
        }
    }
}
