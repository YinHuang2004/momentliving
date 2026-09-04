package com.momentliving.controller;

import com.momentliving.dto.AiChatDTO;
import com.momentliving.result.Result;
import com.momentliving.service.AiChatService;
import com.momentliving.vo.AiMessageVO;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 对话接口（C 端，需用户登录：网关对 /ai/** 鉴权）
 */
@RestController
@RequestMapping("/ai")
public class AiChatController {

    @Resource
    private AiChatService aiChatService;

    /** 同步对话（不支持 SSE 的客户端用） */
    @PostMapping("/chat")
    public Result<AiMessageVO> chat(@RequestBody AiChatDTO dto) {
        return Result.success(aiChatService.chat(dto));
    }

    /**
     * 流式对话（SSE，逐字输出）。
     * 事件约定：meta=会话ID（JSON）→ 默认 data=文本增量 → done=[DONE] / error=友好提示。
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam String message,
                                 @RequestParam(required = false) Long conversationId) {
        AiChatDTO dto = new AiChatDTO();
        dto.setMessage(message);
        dto.setConversationId(conversationId);
        SseEmitter emitter = new SseEmitter(60_000L);
        aiChatService.chatStream(dto, emitter);
        return emitter;
    }
}
