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
 * JWT 登录鉴权拦截器。
 * <p>
 * 所有进入 `/api/**` 的请求都会先经过这里。它负责检查 Token 是否有效，
 * 并根据接口路径判断当前角色是否允许访问。
 * </p>
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** 前端传 Token 时必须使用的请求头前缀：Authorization: Bearer xxx */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 简单的路径-角色权限表。
     * key 是接口路径前缀，value 是允许访问这个模块的角色集合。
     */
    private static final Map<String, Set<String>> ROLE_RULES = Map.of(
            "/api/system", Set.of(UserRole.SYSTEM_ADMIN),
            "/api/production", Set.of(UserRole.SYSTEM_ADMIN, UserRole.PRODUCTION_DISPATCHER),
            "/api/energy", Set.of(UserRole.SYSTEM_ADMIN, UserRole.ENERGY_MANAGER)
    );

    /**
     * 每次请求进入 Controller 前执行。
     * <p>
     * 返回 true 表示放行请求；返回 false 表示已经写入错误响应，请求不会继续进入 Controller。
     * </p>
     */
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

        // 把 Token 中的用户信息挂到 request 上，后续 Controller 或日志审计可以直接取用。
        request.setAttribute("userId", JwtUtils.getUserId(token));
        request.setAttribute("username", JwtUtils.getUsername(token));
        request.setAttribute("role", role);
        log.debug("Token verified: uri={}, role={}", uri, role);
        return true;
    }

    /**
     * 判断当前请求是否属于公开请求。
     * <p>
     * 登录接口、接口文档和浏览器跨域预检请求不需要登录态。
     * </p>
     */
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

    /**
     * 从 Authorization 请求头中取出真实 JWT 字符串。
     * <p>
     * 只有 `Bearer xxx` 格式才认为有效，其他格式统一按未登录处理。
     * </p>
     */
    private String normalizeToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 按接口路径和角色判断是否允许访问。
     * <p>
     * 配置在 ROLE_RULES 中的模块会校验角色；没有配置的模块只要求已登录。
     * </p>
     */
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

    /**
     * 写入统一 JSON 错误响应。
     * <p>
     * 拦截器发生在 Controller 之前，不能依赖全局异常处理器，所以这里直接写响应体。
     * </p>
     */
    private void writeJson(HttpServletResponse response, int httpStatus, Result<?> result) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(result));
    }
}
