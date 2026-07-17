package com.xq.common.exception;

import lombok.Getter;

/**
 * 业务异常.
 * <p>
 * 用于在 Service 层抛出可预期的业务错误，
 * 由全局异常处理器统一捕获并转换为 {@link com.xq.common.result.Result}.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务状态码 */
    private final int code;

    /**
     * @param code    业务状态码
     * @param message 错误描述
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 默认状态码 500.
     *
     * @param message 错误描述
     */
    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }
}
