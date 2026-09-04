package com.momentliving.config;

import com.momentliving.constant.MessageConstant;
import com.momentliving.exception.BaseException;
import com.momentliving.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器（与各服务风格一致）。
 * 大模型调用的超时/限流/Key 无效等异常在 Service 内捕获并转为友好提示，不会走到这里。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常（BaseException 及其子类） */
    @ExceptionHandler
    public Result<Void> handleBaseException(BaseException ex) {
        log.error("业务异常：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /** 路径参数/请求参数类型不匹配 → 400 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.error("参数类型不匹配：{} = {}", ex.getName(), ex.getValue());
        String typeName = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "正确类型";
        return Result.error("参数类型不匹配：" + ex.getName() + " 应为 " + typeName);
    }

    /** 兜底捕获未预料的运行时异常 */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException ex) {
        log.error("系统异常：", ex);
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }

    /** 缺少必填请求参数 → 400 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException ex) {
        log.error("缺少请求参数：{}", ex.getParameterName());
        return Result.error("缺少必填参数：" + ex.getParameterName());
    }

    /** 请求体 JSON 格式错误 / 参数校验失败 → 400 */
    @ExceptionHandler({MethodArgumentNotValidException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBadRequest(Exception ex) {
        log.error("请求参数格式错误：", ex);
        return Result.error("请求参数格式错误，请检查 JSON 格式与字段类型");
    }

    /** 静态资源不存在（文档页图标等）→ 404，避免被兜底成"系统异常" */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(NoResourceFoundException ex) {
        return Result.error("资源不存在");
    }
}
