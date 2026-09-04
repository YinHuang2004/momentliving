package com.momentliving.service;

import com.momentliving.entity.AiConversation;
import com.momentliving.entity.AiMessage;
import com.momentliving.vo.AiConversationVO;

import java.util.List;

/**
 * AI 会话管理：列表 / 新建 / 删除 / 历史消息（所有权校验在实现内）
 */
public interface AiConversationService {

    /** 当前身份的会话列表（按最后更新时间倒序） */
    List<AiConversationVO> listConversations();

    /** 新建会话 */
    AiConversation createConversation();

    /** 删除会话（连同消息），校验所有权 */
    void deleteConversation(Long conversationId);

    /** 会话历史消息（按 id 正序），校验所有权 */
    List<AiMessage> listMessages(Long conversationId);

    /** 获取或创建会话（供对话接口使用）；ownerId/userType 由当前身份决定 */
    AiConversation getOrCreate(Long conversationId);

    /** 追加一条消息并返回 */
    AiMessage appendMessage(Long conversationId, String role, String content);

    /** 对话结束后更新会话的 lastMessage / updatedAt */
    void touchConversation(Long conversationId, String lastMessage);

    /** 异步用 AI 生成会话标题（仅当标题还是默认"新对话"时） */
    void generateTitleAsync(Long conversationId, String firstUserMessage);

    /** 提交对某条 assistant 回答的反馈（校验消息归属） */
    void saveFeedback(com.momentliving.dto.AiFeedbackDTO dto);
}
