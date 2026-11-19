package com.momentliving.ai.common;

import com.momentliving.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：所有异常都转 HTTP 200 + Result.error（code=0）。
 * 与 Python 版 exception_handlers.py 行为对齐（前端 axios 拦截器只看 body.code 的老契约）。
 *
 * <p>⚠️ SSE 流内异常不走这里（流已开始响应），由 ChatService 在流内发 error 事件。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<Void> business(BusinessException e) {
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> fallback(Exception e) {
        log.error("AI 服务未捕获异常", e);
        return Result.error("系统繁忙，请稍后重试");
    }
}
