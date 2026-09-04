package com.momentliving.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.momentliving.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /**
     * 首条限制解除（CAS 式条件更新）：接收方回复（或主动开口）后，INIT/WAIT_REPLY → FREE。
     * 只翻转一个布尔状态，条件更新本身就是并发安全的，不需要分布式锁。
     *
     * @return 影响行数（0=已经是 FREE）
     */
    @Update("update chat_session set first_reply = 1 where id = #{id} and first_reply in (0, 2)")
    int markFirstReplied(@Param("id") Long id);

    /**
     * 发起方发出首条（INIT → WAIT_REPLY）：CAS 保证并发下只成功一次。
     * 同时落真实发起方：INIT 状态 = 还没有任何消息发出，"谁发首条谁就是发起方"，
     * 不信表里可能被旧数据迁移猜错的 initiator_id，顺带修正。
     *
     * @return 影响行数（0=状态已被并发请求推进，调用方应按 WAIT_REPLY 拒绝）
     */
    @Update("update chat_session set first_reply = 2, initiator_id = #{senderId} where id = #{id} and first_reply = 0")
    int markInitiatorSent(@Param("id") Long id, @Param("senderId") Long senderId);

    /**
     * 修正会话的真实发起方（WAIT_REPLY 状态下发现表中 initiator_id 与首条消息发送者不一致时回写）
     */
    @Update("update chat_session set initiator_id = #{senderId} where id = #{id}")
    int updateInitiator(@Param("id") Long id, @Param("senderId") Long senderId);

    /**
     * 收到新消息后刷新会话预览（会话列表展示 + last_message_at 排序）
     */
    @Update("update chat_session set last_message = #{preview}, last_message_at = now() where id = #{id}")
    int touchLastMessage(@Param("id") Long id, @Param("preview") String preview);
}
