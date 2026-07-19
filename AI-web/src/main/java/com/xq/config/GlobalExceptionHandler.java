package com.xq.config;

import com.xq.common.exception.BusinessException;
import com.xq.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.format.DateTimeParseException;

/**
 * 全局异常处理器.
 * <p>
 * 统一捕获 Controller 层异常并转换为统一响应格式.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常.
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常 [{}] {}: {}", request.getRequestURI(), e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<?> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("参数错误 [{}]: {}", request.getRequestURI(), e.getMessage());
        return Result.badRequest(e.getMessage());
    }

    /**
     * Bean Validation 参数校验异常.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : error.getField() + " 参数错误")
                .orElse("请求参数校验失败");
        log.warn("参数校验失败 [{}]: {}", request.getRequestURI(), message);
        return Result.badRequest(message);
    }

    /**
     * 请求体 JSON 为空或格式错误.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleHttpMessageNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("请求体错误 [{}]: {}", request.getRequestURI(), e.getMessage());
        return Result.badRequest("请求体不能为空，且必须是合法 JSON");
    }

    /**
     * 日期格式错误.
     */
    @ExceptionHandler(DateTimeParseException.class)
    public Result<?> handleDateTimeParseException(DateTimeParseException e, HttpServletRequest request) {
        log.warn("日期格式错误 [{}]: {}", request.getRequestURI(), e.getParsedString());
        return Result.badRequest("日期格式错误，应为 yyyy-MM-dd，例如 2026-07-17");
    }

    /**
     * 参数类型不匹配，例如把 /api/tasks/{taskId} 原样发送导致 {taskId} 无法转成数字.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<?> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e,
                                                      HttpServletRequest request) {
        log.warn("参数类型错误 [{}]: {}={}", request.getRequestURI(), e.getName(), e.getValue());
        return Result.badRequest("参数 " + e.getName() + " 必须填写正确的数字 ID，不能直接使用 {" + e.getName() + "}");
    }

    /**
     * 未知异常.
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 [{}]", request.getRequestURI(), e);
        return Result.fail("系统内部错误");
    }
}
