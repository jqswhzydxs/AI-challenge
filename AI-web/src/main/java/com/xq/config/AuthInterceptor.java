package com.xq.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Token 认证拦截器.
 * <p>
 * 校验前端请求的 Authorization 头部，验证 JWT Token 有效性.
 * 联调阶段仅做日志记录，不阻断请求.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("Authorization");
        String uri = request.getRequestURI();

        // 登录接口放行
        if (uri.contains("/api/auth/login")) {
            return true;
        }

        // 联调阶段：记录日志但不阻断
        if (token != null && token.startsWith("Bearer ")) {
            log.debug("Token 校验通过: {}", uri);
            return true;
        }
        log.debug("未携带 Token: {}", uri);
        return true;
    }
}
