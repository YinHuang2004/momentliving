package com.momentliving.controller;

import com.momentliving.dto.AiChatDTO;
import com.momentliving.entity.AiConversation;
import com.momentliving.entity.AiMessage;
import com.momentliving.result.Result;
import com.momentliving.service.AiConversationService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 会话管理（C 端 / 商家端通用，身份由 ThreadLocal 决定）
 */
@RestController
@RequestMapping("/ai/conversations")
public class AiConversationController {

    @Resource
    private AiConversationService conversationService;

    /** 会话列表（按最后消息时间倒序） */
    @GetMapping
    public Result<?> conversations() {
        return Result.success(conversationService.listConversations());
    }

    /** 新建会话 */
    @PostMapping
    public Result<AiConversation> create() {
        return Result.success(conversationService.createConversation());
    }

    /** 删除会话（连同历史消息） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        conversationService.deleteConversation(id);
        return Result.success();
    }

    /** 会话历史消息 */
    @GetMapping("/{id}/messages")
    public Result<List<AiMessage>> messages(@PathVariable Long id) {
        return Result.success(conversationService.listMessages(id));
    }
}
