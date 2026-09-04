package com.momentliving.service;

import com.momentliving.dto.AiChatDTO;
import com.momentliving.vo.AiMessageVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 对话：多轮记忆 + RAG + Function Calling，提供同步与 SSE 流式两种出口
 */
public interface AiChatService {

    /** 同步对话（POST /ai/chat） */
    AiMessageVO chat(AiChatDTO dto);

    /** 流式对话（GET /ai/chat/stream，SSE；结束时会保存完整回复并更新会话） */
    void chatStream(AiChatDTO dto, SseEmitter emitter);
}
