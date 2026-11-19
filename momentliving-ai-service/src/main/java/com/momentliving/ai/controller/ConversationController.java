package com.momentliving.ai.controller;

import com.momentliving.ai.common.UserIdentity;
import com.momentliving.ai.dto.ConversationVO;
import com.momentliving.ai.dto.MessageVO;
import com.momentliving.ai.service.ConversationService;
import com.momentliving.result.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话管理接口。
 */
@RestController
@RequestMapping("/ai/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public Result<List<ConversationVO>> list(
            @RequestHeader(value = UserIdentity.HEADER_USER, required = false) String user,
            @RequestHeader(value = UserIdentity.HEADER_MERCHANT, required = false) String merchant) {
        UserIdentity identity = UserIdentity.of(user, merchant, null).requireChatUser();
        return Result.success(conversationService.list(identity));
    }

    @PostMapping
    public Result<ConversationVO> create(
            @RequestHeader(value = UserIdentity.HEADER_USER, required = false) String user,
            @RequestHeader(value = UserIdentity.HEADER_MERCHANT, required = false) String merchant) {
        UserIdentity identity = UserIdentity.of(user, merchant, null).requireChatUser();
        ConversationService.Row row = conversationService.getOrCreate(identity, null);
        return Result.success(new ConversationVO(
                row.id(), row.userId(), row.userType(), row.title(),
                row.lastMessage(), ConversationService.iso(row.createdAt()),
                ConversationService.iso(row.updatedAt())));
    }

    @DeleteMapping("/{conversationId}")
    public Result<Void> delete(
            @RequestHeader(value = UserIdentity.HEADER_USER, required = false) String user,
            @RequestHeader(value = UserIdentity.HEADER_MERCHANT, required = false) String merchant,
            @PathVariable long conversationId) {
        UserIdentity identity = UserIdentity.of(user, merchant, null).requireChatUser();
        conversationService.delete(identity, conversationId);
        return Result.success();
    }

    @GetMapping("/{conversationId}/messages")
    public Result<List<MessageVO>> messages(
            @RequestHeader(value = UserIdentity.HEADER_USER, required = false) String user,
            @RequestHeader(value = UserIdentity.HEADER_MERCHANT, required = false) String merchant,
            @PathVariable long conversationId) {
        UserIdentity identity = UserIdentity.of(user, merchant, null).requireChatUser();
        return Result.success(conversationService.messages(identity, conversationId));
    }
}
