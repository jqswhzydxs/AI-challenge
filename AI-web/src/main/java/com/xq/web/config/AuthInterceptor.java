package com.xq.web.config;

import com.alibaba.fastjson2.JSON;
import com.xq.common.constant.UserRole;
import com.xq.common.result.Result;
import com.xq.common.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Token authentication interceptor.
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Map<String, Set<String>> ROLE_RULES = Map.of(
            "/api/system", Set.of(UserRole.SYSTEM_ADMIN),
            "/api/production", Set.of(UserRole.SYSTEM_ADMIN, UserRole.PRODUCTION_DISPATCHER),
            "/api/energy", Set.of(UserRole.SYSTEM_ADMIN, UserRole.ENERGY_MANAGER)
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String uri = request.getRequestURI();
        if (isPublicRequest(request, uri)) {
            return true;
        }

        String token = normalizeToken(request.getHeader("Authorization"));
        if (token == null || JwtUtils.isExpired(token)) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, Result.unauthorized("未登录或 Token 已失效"));
            return false;
        }

        String role = JwtUtils.getRole(token);
        if (!hasAccess(uri, role)) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, Result.forbidden("当前角色无权访问该接口"));
            return false;
        }

        request.setAttribute("userId", JwtUtils.getUserId(token));
        request.setAttribute("username", JwtUtils.getUsername(token));
        request.setAttribute("role", role);
        log.debug("Token verified: uri={}, role={}", uri, role);
        return true;
    }

    private boolean isPublicRequest(HttpServletRequest request, String uri) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return uri.contains("/api/auth/login")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/doc.html")
                || uri.startsWith("/webjars");
    }

    private String normalizeToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private boolean hasAccess(String uri, String role) {
        if (role == null || role.trim().isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Set<String>> rule : ROLE_RULES.entrySet()) {
            if (uri.startsWith(rule.getKey())) {
                return rule.getValue().contains(role);
            }
        }
        return true;
    }

    private void writeJson(HttpServletResponse response, int httpStatus, Result<?> result) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(result));
    }
}
