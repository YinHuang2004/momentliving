package com.momentliving.config;

import com.momentliving.constant.MessageConstant;
import com.momentliving.exception.BaseException;
import com.momentliving.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 捕获所有业务异常（BaseException 及其子类）
     */
    @ExceptionHandler
    public Result<Void> handleBaseException(BaseException ex) {
        log.error("业务异常：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 路径参数/请求参数类型不匹配（例如 /xxx/undefined 无法转 Long）→ 400
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.error("参数类型不匹配：{} = {}", ex.getName(), ex.getValue());
        String typeName = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "正确类型";
        return Result.error("参数类型不匹配：" + ex.getName() + " 应为 " + typeName);
    }

    /**
     * 兜底捕获未预料的运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException ex) {
        log.error("系统异常：", ex);
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }

    /**
     * 请求方法不匹配（例如用 GET 请求了 POST 接口）→ 405
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.error("请求方法不支持：{}", ex.getMessage());
        return Result.error("请求方法不支持，请检查 HTTP 方法（POST/GET/PUT/DELETE）是否正确");
    }

    /**
     * 缺少必填请求参数（例如 @RequestParam 参数没传）→ 400
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException ex) {
        log.error("缺少请求参数：{}", ex.getParameterName());
        return Result.error("缺少必填参数：" + ex.getParameterName());
    }

    /**
     * 请求体 JSON 格式错误 / 参数校验失败 → 400
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBadRequest(Exception ex) {
        log.error("请求参数格式错误：", ex);
        return Result.error("请求参数格式错误，请检查 JSON 格式与字段类型");
    }
}
