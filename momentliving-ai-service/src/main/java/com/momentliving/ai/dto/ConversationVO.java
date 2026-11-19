package com.momentliving.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 会话 VO：列表接口只填 id/title/lastMessage/updatedAt，
 * 新建接口填全部字段（NON_NULL 序列化保证与 Python 两种 VO 形状一致）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConversationVO(
        long id,
        Long userId,
        Integer userType,
        String title,
        String lastMessage,
        String createdAt,
        String updatedAt) {
}
