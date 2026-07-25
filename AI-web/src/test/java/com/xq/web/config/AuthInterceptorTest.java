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

    @Test
    void rejectsMissingTokenWithUnifiedJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/overview");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":401"));
    }

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

    @Test
    void allowsPreflightRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/system/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }
}
