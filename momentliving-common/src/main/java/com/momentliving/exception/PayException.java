package com.momentliving.exception;

/**
 * 支付业务异常（预下单失败、渠道未配置、回调验签不通过等）
 * 全局异常处理器会把它转成 Result.error(msg) 返回给前端
 */
public class PayException extends BaseException {

    public PayException(String msg) {
        super(msg);
    }
}
