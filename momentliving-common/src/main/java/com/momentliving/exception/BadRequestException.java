package com.momentliving.exception;

/**
 * 请求参数异常
 */
public class BadRequestException extends BaseException {

    public BadRequestException(String msg) {
        super(msg);
    }
}
