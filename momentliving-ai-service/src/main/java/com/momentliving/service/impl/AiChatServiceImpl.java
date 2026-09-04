package com.momentliving.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.momentliving.config.AiProperties;
import com.momentliving.context.UserHolder;
import com.momentliving.dto.AiChatDTO;
import com.momentliving.entity.AiConversation;
import com.momentliving.entity.AiMessage;
import com.momentliving.exception.BadRequestException;
import com.momentliving.mapper.AiMessageMapper;
import com.momentliving.prompt.AiPromptConstants;
import com.momentliving.service.AiChatService;
import com.momentliving.service.AiConversationService;
import com.momentliving.service.AiKnowledgeService;
import com.momentliving.tools.BlogTools;
import com.momentliving.tools.ShopTools;
import com.momentliving.tools.VoucherTools;
import com.momentliving.vo.AiMessageVO;
import com.momentliving.vo.UserVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 对话实现：多轮记忆（最近 N 轮）+ RAG 知识检索 + Function Calling 实时数据。
 *
 * <p>安全设计：
 * - 用户输入只作为 User Message，禁止拼入 System Prompt（防 Prompt 注入）；
 * - 工具查询"用户数据"时身份一律走 ToolContext（userId 由服务端写入，AI 决定不了查谁）。
 */
@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    private static final String AI_UNAVAILABLE = "AI 服务暂时不可用，请稍后重试";

    @Resource
    private ChatClient chatClient;

    @Resource
    private AiProperties aiProperties;

    @Resource
    private AiConversationService conversationService;

    @Resource
    private AiKnowledgeService knowledgeService;

    @Resource
    private AiMessageMapper messageMapper;

    @Resource
    private ShopTools shopTools;

    @Resource
    private BlogTools blogTools;

    @Resource
    private VoucherTools voucherTools;

    @Resource(name = "aiExecutor")
    private ThreadPoolTaskExecutor aiExecutor;

    @Override
    public AiMessageVO chat(AiChatDTO dto) {
        checkEnabled();
        String message = validate(dto);
        UserVO user = UserHolder.getUser();

        // 1. 会话（无则建）+ 保存用户消息
        AiConversation conversation = conversationService.getOrCreate(dto.getConversationId());
        conversationService.appendMessage(conversation.getId(), "user", message);

        try {
            // 2. 历史记忆 + RAG 检索
            List<Message> history = loadHistory(conversation.getId());
            String knowledge = aiProperties.isRagEnabled()
                    ? knowledgeService.retrieveContext(message, aiProperties.getRagTopK(), aiProperties.getKnowledgeMaxChars())
                    : "";

            // 3. 调用大模型（携带工具）
            long start = System.currentTimeMillis();
            String reply = chatClient.prompt()
                    .system(buildSystem(knowledge))
                    .messages(history)
                    .user(message)
                    .tools(shopTools, blogTools, voucherTools)
                    .toolContext(Map.of("aiUserId", String.valueOf(user.getId())))
                    .call()
                    .content();
            log.info("AI对话完成 userId={}, conversationId={}, costMs={}",
                    user.getId(), conversation.getId(), System.currentTimeMillis() - start);

            // 4. 持久化 assistant 回复 + 更新会话
            AiMessage saved = conversationService.appendMessage(conversation.getId(), "assistant", reply);
            conversationService.touchConversation(conversation.getId(), reply);
            conversationService.generateTitleAsync(conversation.getId(), message);
            return toVO(saved);
        } catch (Exception e) {
            log.error("AI调用失败 userId={}, conversationId={}", user.getId(), conversation.getId(), e);
            throw new BadRequestException(AI_UNAVAILABLE);
        }
    }

    @Override
    public void chatStream(AiChatDTO dto, SseEmitter emitter) {
        checkEnabled();
        String message = validate(dto);
        UserVO user = UserHolder.getUser();

        // 会话与用户消息在当前线程先落库（失败直接报错，不进入流）
        AiConversation conversation = conversationService.getOrCreate(dto.getConversationId());
        conversationService.appendMessage(conversation.getId(), "user", message);

        List<Message> history = loadHistory(conversation.getId());
        String knowledge = aiProperties.isRagEnabled()
                ? knowledgeService.retrieveContext(message, aiProperties.getRagTopK(), aiProperties.getKnowledgeMaxChars())
                : "";
        Long userId = user.getId();
        Long conversationId = conversation.getId();

        aiExecutor.execute(() -> {
            StringBuilder full = new StringBuilder();
            try {
                // 先推一条 meta 事件：前端拿到会话 ID 才能继续在同一会话追问
                emitter.send(SseEmitter.event().name("meta").data("{\"conversationId\":" + conversationId + "}"));

                chatClient.prompt()
                        .system(buildSystem(knowledge))
                        .messages(history)
                        .user(message)
                        .tools(shopTools, blogTools, voucherTools)
                        .toolContext(Map.of("aiUserId", String.valueOf(userId)))
                        .stream()
                        .content()
                        .doOnNext(chunk -> {
                            full.append(chunk);
                            try {
                                emitter.send(SseEmitter.event().data(chunk));
                            } catch (IOException e) {
                                // 客户端已断开连接（用户关闭页面/网络中断），属于正常行为，
                                // 直接完成 emitter 即可，不抛 RuntimeException 避免污染 error 日志
                                log.debug("SSE客户端断开连接，停止推送 userId={}, conversationId={}", userId, conversationId);
                                emitter.complete();
                            }
                        })
                        .doOnComplete(() -> {
                            try {
                                // 流结束：拼接完整回复落库
                                if (!full.isEmpty()) {
                                    conversationService.appendMessage(conversationId, "assistant", full.toString());
                                    conversationService.touchConversation(conversationId, full.toString());
                                    conversationService.generateTitleAsync(conversationId, message);
                                }
                                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                                emitter.complete();
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnError(e -> {
                            log.error("AI流式对话异常 userId={}, conversationId={}", userId, conversationId, e);
                            try {
                                emitter.send(SseEmitter.event().name("error").data(AI_UNAVAILABLE));
                            } catch (IOException ignore) {
                                // 客户端已断开
                            }
                            emitter.complete();
                        })
                        .subscribe();
            } catch (Exception e) {
                log.error("AI流式对话启动失败 userId={}, conversationId={}", userId, conversationId, e);
                emitter.completeWithError(e);
            }
        });
    }

    /** System Prompt：基础人设 + RAG 知识片段（知识片段来自知识库文档，非用户输入，防注入） */
    private String buildSystem(String knowledge) {
        if (knowledge == null || knowledge.isBlank()) {
            return AiPromptConstants.C_END_SYSTEM;
        }
        return AiPromptConstants.C_END_SYSTEM + AiPromptConstants.RAG_CONTEXT_TEMPLATE.formatted(knowledge);
    }

    /** 加载最近 N 轮历史（排除刚保存的这条 user 消息），转为 Spring AI Message */
    private List<Message> loadHistory(Long conversationId) {
        int limit = aiProperties.getHistoryRounds() * 2;
        List<AiMessage> recent = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .in(AiMessage::getRole, "user", "assistant")
                .orderByDesc(AiMessage::getId)
                .last("limit " + (limit + 1)));
        // 降序取出最近 limit+1 条（第 1 条是刚保存的 user 消息），去掉后反转成正序
        List<Message> history = new ArrayList<>();
        for (int i = 1; i < recent.size() && history.size() < limit; i++) {
            AiMessage m = recent.get(i);
            if ("user".equals(m.getRole())) {
                history.add(new UserMessage(m.getContent()));
            } else {
                history.add(new AssistantMessage(m.getContent()));
            }
        }
        java.util.Collections.reverse(history);
        return history;
    }

    private void checkEnabled() {
        if (!aiProperties.isEnabled()) {
            throw new BadRequestException("AI 功能未开启");
        }
    }

    private String validate(AiChatDTO dto) {
        if (dto == null || dto.getMessage() == null || dto.getMessage().isBlank()) {
            throw new BadRequestException("消息内容不能为空");
        }
        if (dto.getMessage().length() > 2000) {
            throw new BadRequestException("消息内容过长（最多 2000 字）");
        }
        return dto.getMessage().trim();
    }

    private AiMessageVO toVO(AiMessage message) {
        return AiMessageVO.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .role(message.getRole())
                .content(message.getContent())
                .toolCalls(message.getToolCalls())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
