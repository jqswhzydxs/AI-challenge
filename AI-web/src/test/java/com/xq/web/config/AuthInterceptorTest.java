package com.xq.web.config;

import com.xq.common.constant.UserRole;
import com.xq.common.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthInterceptorTest {

    private final AuthInterceptor interceptor = new AuthInterceptor();

    /**
     * 验证没有 Authorization 请求头时，拦截器会返回统一 401 JSON。
     */
    @Test
    void rejectsMissingTokenWithUnifiedJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/overview");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":401"));
    }

    /**
     * 验证用户已登录但角色不匹配时，拦截器会返回 403。
     */
    @Test
    void rejectsRoleWithoutModulePermission() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/system/users");
        request.addHeader("Authorization", "Bearer " + JwtUtils.createToken(2L, "energy", UserRole.ENERGY_MANAGER));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":403"));
    }

    /**
     * 验证角色有权限时请求可以继续进入 Controller，并把用户信息写入 request。
     */
    @Test
    void allowsRoleWithModulePermissionAndSetsRequestAttributes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/energy/device-status");
        request.addHeader("Authorization", "Bearer " + JwtUtils.createToken(3L, "energy", UserRole.ENERGY_MANAGER));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertTrue(allowed);
        assertEquals(200, response.getStatus());
        assertEquals(3L, request.getAttribute("userId"));
        assertEquals("energy", request.getAttribute("username"));
        assertEquals(UserRole.ENERGY_MANAGER, request.getAttribute("role"));
    }

    /**
     * 验证浏览器 CORS 预检请求不需要 Token，避免前端跨域请求被提前拦截。
     */
    @Test
    void allowsPreflightRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/system/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }
}
