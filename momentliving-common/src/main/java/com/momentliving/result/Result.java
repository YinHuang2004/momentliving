package com.momentliving.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一返回结果
 *
 * <p>结构约定：
 * <pre>
 * {
 *   "code": 1,        // 1 = 成功，0 = 失败
 *   "msg": "success", // 提示信息
 *   "data": {...}     // 实际业务数据（可选）
 * }
 * </pre>
 *
 * <p>工厂方法：
 * <ul>
 *   <li>{@link #success()} —— 操作成功，无需返回数据（删除、退出登录等）</li>
 *   <li>{@link #success(Object)} —— 操作成功，需返回数据（查询详情、列表等）</li>
 *   <li>{@link #error(String)} —— 操作失败，告知原因（参数校验不通过、权限不足等），常用于全局异常处理器</li>
 * </ul>
 *
 * @param <T> 业务数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** 状态码：1 = 成功，0 = 失败 */
    private Integer code;

    /** 提示信息 */
    private String msg;

    /** 实际业务数据 */
    private T data;

    /**
     * 操作成功，无需返回数据
     * 使用场景：删除用户、退出登录
     */
    public static <T> Result<T> success() {
        return new Result<>(1, "success", null);
    }

    /**
     * 操作成功，需返回数据
     * 使用场景：查询用户信息、获取订单列表
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(1, "success", data);
    }

    /**
     * 操作失败，告知原因
     * 使用场景：参数校验不通过、权限不足，常见于全局异常处理器
     */
    public static <T> Result<T> error(String msg) {
        return new Result<>(0, msg, null);
    }
}
