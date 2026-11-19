package com.momentliving.ai.controller;

import com.momentliving.ai.common.UserIdentity;
import com.momentliving.ai.dto.AiMessageVO;
import com.momentliving.ai.dto.ChatRequest;
import com.momentliving.ai.service.ChatService;
import com.momentliving.result.Result;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话接口：同步 + SSE 流式（事件协议 meta/data/done/error 与 Python 版逐字对齐）。
 */
@RestController
@RequestMapping("/ai")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public Result<AiMessageVO> chat(
            @RequestHeader(value = UserIdentity.HEADER_USER, required = false) String user,
            @RequestHeader(value = UserIdentity.HEADER_MERCHANT, required = false) String merchant,
            @RequestHeader(value = UserIdentity.HEADER_ADMIN, required = false) String admin,
            @RequestBody(required = false) ChatRequest req) {
        UserIdentity identity = UserIdentity.of(user, merchant, admin).requireChatUser();
        return Result.success(chatService.chat(identity, req));
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(
            @RequestHeader(value = UserIdentity.HEADER_USER, required = false) String user,
            @RequestHeader(value = UserIdentity.HEADER_MERCHANT, required = false) String merchant,
            @RequestHeader(value = UserIdentity.HEADER_ADMIN, required = false) String admin,
            @RequestParam("message") String message,
            @RequestParam(value = "conversationId", required = false) Long conversationId) {
        UserIdentity identity = UserIdentity.of(user, merchant, admin).requireChatUser();
        return chatService.stream(identity, message, conversationId);
    }
}
