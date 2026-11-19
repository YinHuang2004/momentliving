package com.momentliving.ai.service;

import com.momentliving.ai.common.BusinessException;
import com.momentliving.ai.common.UserIdentity;
import com.momentliving.ai.config.AiProperties;
import com.momentliving.ai.dto.AiMessageVO;
import com.momentliving.ai.dto.ChatRequest;
import com.momentliving.ai.prompt.Prompts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 对话服务：同步 chat + SSE 流式 stream。
 *
 * <p>链路（与 Python 版对齐）：参数校验 → 会话归属校验 → ChatClient
 * （Advisor 链内完成 历史注入 + RAG 知识注入，消息落库由混合记忆 Advisor 完成）
 * → 触活会话 → 异步生成标题。工具调用循环由 Spring AI 内置执行
 * （替代 Python 版手工 6 轮 bind_tools 循环）。
 *
 * <p>⚠️ 用户输入只进 user message，不拼 system prompt（防 Prompt 注入）；
 * 身份经 toolContext 显式传给工具，AI 决定不了查谁。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final ChatClient chatClient;
    private final ConversationService conversationService;
    private final TitleGenerator titleGenerator;
    private final AiProperties props;
    private final JdbcTemplate jdbc;

    public ChatService(ChatClient chatClient, ConversationService conversationService,
                       TitleGenerator titleGenerator, AiProperties props, JdbcTemplate jdbc) {
        this.chatClient = chatClient;
        this.conversationService = conversationService;
        this.titleGenerator = titleGenerator;
        this.props = props;
        this.jdbc = jdbc;
    }

    /** 同步对话（POST /ai/chat）。 */
    public AiMessageVO chat(UserIdentity identity, ChatRequest req) {
        checkEnabled();
        String message = validate(req == null ? null : req.message());
        ConversationService.Row conv = conversationService.getOrCreate(identity, req.conversationId());
        String convId = String.valueOf(conv.id());

        String content;
        try {
            content = chatClient.prompt()
                    .user(message)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId))
                    .toolContext(toolContext(identity))
                    .call()
                    .content();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM 调用失败，conversationId={}", conv.id(), e);
            throw new BusinessException(Prompts.AI_UNAVAILABLE);
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException(Prompts.AI_UNAVAILABLE);
        }

        conversationService.touch(conv.id(), content);
        titleGenerator.generate(conv.id(), message);

        // 取刚落库的 assistant 消息 id/时间组装 VO（与 Python RETURNING 行为对齐）
        Long id = jdbc.query(
                "SELECT id, created_at FROM ai_message WHERE conversation_id = ? AND role = 'assistant' ORDER BY id DESC LIMIT 1",
                rs -> {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                    return 0L;
                }, conv.id());
        LocalDateTime createdAt = jdbc.query(
                "SELECT created_at FROM ai_message WHERE conversation_id = ? AND role = 'assistant' ORDER BY id DESC LIMIT 1",
                rs -> rs.next() ? rs.getTimestamp(1).toLocalDateTime() : LocalDateTime.now(), conv.id());
        return new AiMessageVO(id == null ? 0 : id, conv.id(), "assistant", content, null,
                ConversationService.iso(createdAt));
    }

    /** 流式对话（GET /ai/chat/stream）——SSE 事件协议：meta → data* → done / error。 */
    public Flux<ServerSentEvent<String>> stream(UserIdentity identity, String message, Long conversationId) {
        checkEnabled();
        String msg = validate(message);
        ConversationService.Row conv = conversationService.getOrCreate(identity, conversationId);
        String convId = String.valueOf(conv.id());

        StringBuilder full = new StringBuilder();
        Flux<ServerSentEvent<String>> chunks = chatClient.prompt()
                .user(msg)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId))
                .toolContext(toolContext(identity))
                .stream()
                .chatResponse()
                .map(this::chunkText)
                .doOnNext(full::append)
                .filter(t -> !t.isEmpty())
                .map(t -> ServerSentEvent.<String>builder().data(t).build());

        return Flux.concat(
                // 1. meta：会话 id（历史加载/RAG 检索之后、LLM 输出之前）
                Flux.just(metaEvent(conv.id())),
                chunks,
                // 2. done：触发于内容流 complete 之后（此时记忆 Advisor 已完成落库）
                Flux.defer(() -> {
                    if (full.length() > 0) {
                        conversationService.touch(conv.id(), full.toString());
                        titleGenerator.generate(conv.id(), msg);
                    }
                    return Flux.just(ServerSentEvent.<String>builder().event("done").data("[DONE]").build());
                })
        ).onErrorResume(e -> {
            log.error("SSE 流式对话异常，conversationId={}", conv.id(), e);
            String tip = e instanceof BusinessException be ? be.getMessage() : Prompts.AI_UNAVAILABLE;
            return Flux.just(ServerSentEvent.<String>builder().event("error").data(tip).build());
        });
    }

    /** 空安全提取流式 chunk 的文本增量（无内容返回 ""）。 */
    private String chunkText(ChatResponse response) {
        if (response == null || response.getResult() == null
                || response.getResult().getOutput() == null
                || response.getResult().getOutput().getText() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    private ServerSentEvent<String> metaEvent(long conversationId) {
        return ServerSentEvent.<String>builder()
                .event("meta")
                .data("{\"conversationId\":" + conversationId + "}")
                .build();
    }

    /** 工具上下文：身份由服务端写入，工具只能查当前请求人的数据。 */
    private Map<String, Object> toolContext(UserIdentity identity) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("userId", identity.chatUserId());
        if (identity.merchantId() != null) {
            ctx.put("merchantId", identity.merchantId());
        }
        return ctx;
    }

    private void checkEnabled() {
        if (!props.enabled()) {
            throw new BusinessException("AI 功能未开启");
        }
    }

    /** 校验用户消息：非空且不超过 2000 字。 */
    private String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("消息内容不能为空");
        }
        String message = raw.strip();
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException("消息内容过长（最多 " + MAX_MESSAGE_LENGTH + " 字）");
        }
        return message;
    }
}
