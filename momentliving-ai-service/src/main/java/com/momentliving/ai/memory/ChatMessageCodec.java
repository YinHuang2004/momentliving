package com.momentliving.ai.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * 消息 &lt;-&gt; JSON 序列化。
 *
 * <p>⚠️ 刻意采用 LangChain 标准存储格式：{"type":"human|ai|system","data":{"content":...}}，
 * 与 Python 版 message_codec.py 完全一致——Java 与 Python 两个实现共用同一个
 * Redis 会话窗口（key：ai:conv:{id}），迁移/灰度期间上下文互不丢失。
 */
public final class ChatMessageCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ChatMessageCodec() {
    }

    /** Message → LangChain 标准格式 JSON 字符串。 */
    public static String encode(Message msg) {
        String type;
        if (msg instanceof UserMessage) {
            type = "human";
        } else if (msg instanceof AssistantMessage) {
            type = "ai";
        } else {
            type = "system";
        }
        String content = msg.getText() == null ? "" : msg.getText();
        // 构造 {"type":..,"data":{"content":..,"additional_kwargs":{}}}（与 LangChain 对齐）
        StringBuilder sb = new StringBuilder(64 + content.length());
        sb.append("{\"type\":\"").append(type).append("\",\"data\":{\"content\":")
          .append(quote(content)).append(",\"additional_kwargs\":{}}}");
        return sb.toString();
    }

    /** JSON → Message；类型不识别或解析失败返回 null（调用方丢弃坏数据）。 */
    public static Message decode(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            String type = node.path("type").asText("");
            String content = node.path("data").path("content").asText("");
            return switch (type) {
                case "human" -> new UserMessage(content);
                case "ai" -> new AssistantMessage(content);
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private static String quote(String s) {
        try {
            return MAPPER.writeValueAsString(s);
        } catch (Exception e) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }
}
