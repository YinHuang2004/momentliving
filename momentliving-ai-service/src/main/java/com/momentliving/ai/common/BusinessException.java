package com.momentliving.ai.common;

/**
 * 业务异常：由 GlobalExceptionHandler 统一转为 HTTP 200 + {"code":0,"msg":...}
 * （与 Python 版 / Spring 全家桶契约一致：HTTP 恒 200，业务成败看 body.code）。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
