package com.xq.web.aspect;

import com.alibaba.fastjson2.JSON;
import com.xq.common.exception.BusinessException;
import com.xq.common.result.Result;
import com.xq.mapper.SysOperationLogMapper;
import com.xq.model.entity.SysOperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Arrays;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private static final int MAX_PARAM_LENGTH = 2000;

    private final SysOperationLogMapper operationLogMapper;

    @Around("within(com.xq.web.controller..*)")
    public Object recordOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = currentRequest();
        if (request == null || !shouldRecord(request)) {
            return joinPoint.proceed();
        }

        int resultCode = 200;
        String errorMessage = null;
        try {
            Object result = joinPoint.proceed();
            if (result instanceof Result<?> response) {
                resultCode = response.getCode();
                if (resultCode >= 400) {
                    errorMessage = response.getMessage();
                }
            }
            writeLog(joinPoint, request, resultCode, errorMessage);
            return result;
        } catch (Throwable e) {
            resultCode = e instanceof BusinessException businessException ? businessException.getCode() : 500;
            errorMessage = e.getMessage();
            writeLog(joinPoint, request, resultCode, errorMessage);
            throw e;
        }
    }

    private boolean shouldRecord(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    private void writeLog(ProceedingJoinPoint joinPoint,
                          HttpServletRequest request,
                          int resultCode,
                          String errorMessage) {
        try {
            SysOperationLog log = new SysOperationLog();
            log.setUserId(userId(request));
            log.setModule(moduleName(joinPoint));
            log.setOperation(operationName(joinPoint));
            log.setRequestUri(request.getRequestURI());
            log.setRequestMethod(request.getMethod());
            log.setRequestParam(requestParam(joinPoint));
            log.setResultCode(resultCode);
            log.setErrorMessage(limit(errorMessage, 500));
            log.setOperationTime(LocalDateTime.now());
            operationLogMapper.insert(log);
        } catch (RuntimeException e) {
            log.warn("write operation log failed: {}", e.getMessage());
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private Long userId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId instanceof Long id) {
            return id;
        }
        if (userId instanceof Number number) {
            return number.longValue();
        }
        if (userId instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String moduleName(ProceedingJoinPoint joinPoint) {
        Class<?> controllerClass = joinPoint.getTarget().getClass();
        Tag tag = controllerClass.getAnnotation(Tag.class);
        if (tag != null && !tag.name().isBlank()) {
            return tag.name();
        }
        return controllerClass.getSimpleName();
    }

    private String operationName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Operation operation = signature.getMethod().getAnnotation(Operation.class);
        if (operation != null && !operation.summary().isBlank()) {
            return operation.summary();
        }
        return signature.getMethod().getName();
    }

    private String requestParam(ProceedingJoinPoint joinPoint) {
        Object[] args = Arrays.stream(joinPoint.getArgs())
                .filter(arg -> !(arg instanceof ServletRequest))
                .filter(arg -> !(arg instanceof ServletResponse))
                .toArray();
        return limit(JSON.toJSONString(args), MAX_PARAM_LENGTH);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
