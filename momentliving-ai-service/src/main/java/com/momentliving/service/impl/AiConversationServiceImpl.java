package com.momentliving.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.momentliving.context.MerchantHolder;
import com.momentliving.context.UserHolder;
import com.momentliving.entity.AiConversation;
import com.momentliving.entity.AiFeedback;
import com.momentliving.entity.AiMessage;
import com.momentliving.exception.BadRequestException;
import com.momentliving.mapper.AiConversationMapper;
import com.momentliving.mapper.AiFeedbackMapper;
import com.momentliving.mapper.AiMessageMapper;
import com.momentliving.prompt.AiPromptConstants;
import com.momentliving.service.AiConversationService;
import com.momentliving.vo.AiConversationVO;
import com.momentliving.vo.MerchantVO;
import com.momentliving.vo.UserVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话管理实现。
 * 身份约定：userType 1=C 端用户（UserHolder）；2=商家（MerchantHolder）。
 */
@Slf4j
@Service
public class AiConversationServiceImpl implements AiConversationService {

    @Resource
    private AiConversationMapper conversationMapper;

    @Resource
    private AiMessageMapper messageMapper;

    @Resource
    private AiFeedbackMapper feedbackMapper;

    @Resource
    private ChatModel chatModel;

    @Resource(name = "aiExecutor")
    private ThreadPoolTaskExecutor aiExecutor;

    @Override
    public List<AiConversationVO> listConversations() {
        Long ownerId = currentOwnerId();
        Integer userType = currentUserType();
        List<AiConversation> list = conversationMapper.selectList(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, ownerId)
                .eq(AiConversation::getUserType, userType)
                .orderByDesc(AiConversation::getUpdatedAt)
                .last("limit 50"));
        return list.stream()
                .map(c -> AiConversationVO.builder()
                        .id(c.getId())
                        .title(c.getTitle())
                        .lastMessage(c.getLastMessage())
                        .updatedAt(c.getUpdatedAt())
                        .build())
                .toList();
    }

    @Override
    public AiConversation createConversation() {
        AiConversation conversation = AiConversation.builder()
                .userId(currentOwnerId())
                .userType(currentUserType())
                .title("新对话")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        conversationMapper.insert(conversation);
        return conversation;
    }

    @Override
    public void deleteConversation(Long conversationId) {
        AiConversation conversation = mustGetOwned(conversationId);
        conversationMapper.deleteById(conversation.getId());
        // 连同历史消息一起删
        messageMapper.delete(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId));
    }

    @Override
    public List<AiMessage> listMessages(Long conversationId) {
        mustGetOwned(conversationId);
        return messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByAsc(AiMessage::getId));
    }

    @Override
    public AiConversation getOrCreate(Long conversationId) {
        if (conversationId != null) {
            return mustGetOwned(conversationId);
        }
        return createConversation();
    }

    @Override
    public AiMessage appendMessage(Long conversationId, String role, String content) {
        AiMessage message = AiMessage.builder()
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();
        messageMapper.insert(message);
        return message;
    }

    @Override
    public void touchConversation(Long conversationId, String lastMessage) {
        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            return;
        }
        conversation.setLastMessage(lastMessage != null && lastMessage.length() > 200
                ? lastMessage.substring(0, 200) : lastMessage);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
    }

    @Override
    public void generateTitleAsync(Long conversationId, String firstUserMessage) {
        aiExecutor.execute(() -> {
            try {
                AiConversation conversation = conversationMapper.selectById(conversationId);
                if (conversation == null || !"新对话".equals(conversation.getTitle())) {
                    return; // 已有标题 / 会话已删，跳过
                }
                String title = chatModel.call(AiPromptConstants.GENERATE_TITLE.formatted(firstUserMessage));
                if (title != null && !title.isBlank()) {
                    title = title.trim().replaceAll("[\"'。！!？?\\n]", "");
                    if (title.length() > 20) {
                        title = title.substring(0, 20);
                    }
                    conversation.setTitle(title);
                    conversationMapper.updateById(conversation);
                }
            } catch (Exception e) {
                // 标题生成失败不影响主流程，回退为消息截断
                log.warn("会话标题生成失败 conversationId={}", conversationId, e);
                try {
                    AiConversation conversation = conversationMapper.selectById(conversationId);
                    if (conversation != null && "新对话".equals(conversation.getTitle())
                            && firstUserMessage != null) {
                        conversation.setTitle(firstUserMessage.substring(0, Math.min(12, firstUserMessage.length())));
                        conversationMapper.updateById(conversation);
                    }
                } catch (Exception ignore) {
                    // 彻底失败就保持"新对话"
                }
            }
        });
    }

    @Override
    public void saveFeedback(com.momentliving.dto.AiFeedbackDTO dto) {
        if (dto == null || dto.getMessageId() == null) {
            throw new BadRequestException("messageId 不能为空");
        }
        if (dto.getRating() == null || dto.getRating() < 1 || dto.getRating() > 5) {
            throw new BadRequestException("评分必须为 1-5");
        }
        AiMessage message = messageMapper.selectById(dto.getMessageId());
        if (message == null || !"assistant".equals(message.getRole())) {
            throw new BadRequestException("反馈目标消息不存在");
        }
        // 校验消息归属：该消息所在会话必须是当前身份的
        mustGetOwned(message.getConversationId());
        AiFeedback feedback = AiFeedback.builder()
                .messageId(dto.getMessageId())
                .userId(currentOwnerId())
                .rating(dto.getRating())
                .comment(dto.getComment())
                .createdAt(LocalDateTime.now())
                .build();
        feedbackMapper.insert(feedback);
    }

    /** 所有权校验：会话必须属于当前身份，防止横向越权读取他人会话 */    private AiConversation mustGetOwned(Long conversationId) {
        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new BadRequestException("会话不存在");
        }
        if (!conversation.getUserId().equals(currentOwnerId())
                || !conversation.getUserType().equals(currentUserType())) {
            throw new BadRequestException("无权访问该会话");
        }
        return conversation;
    }

    /** 当前身份 ID：商家态优先（商家 token 访问 /ai/merchant/** 时网关只透传 X-Merchant-Id） */
    private Long currentOwnerId() {
        MerchantVO merchant = MerchantHolder.getMerchant();
        if (merchant != null) {
            return merchant.getId();
        }
        UserVO user = UserHolder.getUser();
        if (user == null) {
            throw new BadRequestException("未登录");
        }
        return user.getId();
    }

    private Integer currentUserType() {
        return MerchantHolder.getMerchant() != null ? 2 : 1;
    }
}
