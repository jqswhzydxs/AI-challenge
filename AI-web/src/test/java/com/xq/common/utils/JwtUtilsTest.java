package com.xq.common.utils;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtUtilsTest {

    @AfterEach
    void tearDown() {
        System.clearProperty("jwt.secret");
        System.clearProperty("jwt.expire-seconds");
        System.clearProperty("jwt.expireSeconds");
    }

    @Test
    void tokenUsesConfiguredSecret() {
        System.setProperty("jwt.secret", "test-secret-for-jwt-signing-at-least-32-bytes-A");
        String token = JwtUtils.createToken(1L, "admin", "SYSTEM_ADMIN");

        assertEquals(1L, JwtUtils.getUserId(token));
        assertEquals("admin", JwtUtils.getUsername(token));
        assertEquals("SYSTEM_ADMIN", JwtUtils.getRole(token));

        System.setProperty("jwt.secret", "test-secret-for-jwt-signing-at-least-32-bytes-B");
        assertNull(JwtUtils.parseToken(token));
    }

    @Test
    void tokenUsesConfiguredExpireSeconds() {
        System.setProperty("jwt.secret", "test-secret-for-jwt-signing-at-least-32-bytes-C");
        System.setProperty("jwt.expire-seconds", "2");

        Claims claims = JwtUtils.parseToken(JwtUtils.createToken(2L, "energy", "ENERGY_MANAGER"));

        assertNotNull(claims);
        assertEquals(2000L, claims.getExpiration().getTime() - claims.getIssuedAt().getTime());
    }
}
