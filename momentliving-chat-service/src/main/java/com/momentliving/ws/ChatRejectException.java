package com.momentliving.ws;

import lombok.Getter;

/**
 * 聊天业务拒绝异常
 *
 * <p>两个出口：
 * 1. WS 发送路径：ChatServiceImpl.send 捕获后转 {op:'reject', code, msg} 帧回给发送者；
 * 2. REST 路径：冒泡到 GlobalExceptionHandler → Result.error(msg)。
 */
@Getter
public class ChatRejectException extends RuntimeException {

    /** 拒绝码：WAIT_REPLY=对方回复前仅可发送一条 / NO_SESSION=会话不存在 / NO_PERMISSION=无权限 */
    public static final String WAIT_REPLY = "WAIT_REPLY";
    public static final String NO_SESSION = "NO_SESSION";
    public static final String NO_PERMISSION = "NO_PERMISSION";
    public static final String ERROR = "ERROR";

    private final String code;

    public ChatRejectException(String code, String message) {
        super(message);
        this.code = code;
    }
}
