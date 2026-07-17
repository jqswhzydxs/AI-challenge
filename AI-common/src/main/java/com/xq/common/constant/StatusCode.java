package com.xq.common.constant;

/*
 * 通用状态码常量.
 * <p>
 * HTTP 状态码 + 算法相关业务状态码（600-699）.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
public final class StatusCode {

    /* 成功 */
    public static final int SUCCESS = 200;
    /* 请求参数错误 */
    public static final int BAD_REQUEST = 400;
    /* 未登录或 Token 失效 */
    public static final int UNAUTHORIZED = 401;
    /* 无权限 */
    public static final int FORBIDDEN = 403;
    /* 数据不存在 */
    public static final int NOT_FOUND = 404;
    /* 系统内部错误 */
    public static final int INTERNAL_ERROR = 500;

    /* 算法服务调用失败 */
    public static final int ALGORITHM_CALL_FAILED = 600;
    /* 算法任务超时 */
    public static final int ALGORITHM_TIMEOUT = 601;
    /* 算法返回结果校验失败 */
    public static final int ALGORITHM_VALIDATION_FAILED = 602;

    private StatusCode() {
    }
}
