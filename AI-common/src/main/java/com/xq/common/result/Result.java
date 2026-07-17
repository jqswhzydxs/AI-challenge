package com.xq.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一响应结果封装.
 * <p>
 * 所有后端返回给前端的接口统一包一层 {@code code / message / data} 结构.
 * </p>
 *
 * @param <T> 响应数据类型
 * @author XQ
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    /** 状态码，参考 {@link com.xq.common.constant.StatusCode} */
    private int code;

    /** 提示信息 */
    private String message;

    /** 响应数据，可为 null */
    private T data;

    /** 成功响应，无数据. */
    public static <T> Result<T> ok() {
        return new Result<>(200, "success", null);
    }

    /** 成功响应，携带数据. */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    /** 成功响应，自定义消息和数据. */
    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(200, message, data);
    }

    /** 失败响应，指定状态码和消息. */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 失败响应，默认状态码 500. */
    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }

    /** 参数错误响应 (400). */
    public static <T> Result<T> badRequest(String message) {
        return new Result<>(400, message, null);
    }

    /** 未登录/Token失效响应 (401). */
    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(401, message, null);
    }

    /** 无权限响应 (403). */
    public static <T> Result<T> forbidden(String message) {
        return new Result<>(403, message, null);
    }

    /** 数据不存在响应 (404). */
    public static <T> Result<T> notFound(String message) {
        return new Result<>(404, message, null);
    }
}
