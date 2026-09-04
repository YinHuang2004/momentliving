package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
    // 定制查询均可用 MyBatis-Plus 的 LambdaQueryWrapper 表达：
    // - 按 clientMsgId 回查（幂等重发场景）：eq(ChatMessage::getClientMsgId, ...)
    // - 历史消息游标分页：eq(sessionId).lt(id, cursor).orderByDesc(id).last("limit n")
    // - 未读计数：eq(isRead,0).ne(senderId,me).in(sessionId, ids)

    /**
     * 查会话首条消息的真实发送者（首条限制状态机的"发起方"以此为准：
     * chat_session.initiator_id 可能是旧数据按 user_a_id 迁移猜出来的，不可信）
     */
    @Select("select sender_id from chat_message where session_id = #{sessionId} order by id limit 1")
    Long selectFirstSender(@Param("sessionId") Long sessionId);
}
