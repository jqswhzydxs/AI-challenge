package com.xq.common.utils;

import cn.hutool.core.util.StrUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类.
 * <p>
 * 负责生成、解析、校验前端请求携带的 Bearer Token.
 * Token 有效期 24 小时，载荷包含 userId、username、role.
 * </p>
 *
 * @author XQ
 * @since 1.0.0
 */
public final class JwtUtils {

    /** JWT 签名密钥 */
    private static final String SECRET = "steel-energy-optimization-platform-jwt-secret-key-2026";
    /** Token 有效期（秒），默认 24 小时 */
    private static final long EXPIRE_SECONDS = 86400L;
    /** 预计算的签名 Key */
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtUtils() {
    }

    /**
     * 创建 JWT Token.
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param role     角色
     * @return JWT Token 字符串
     */
    public static String createToken(Long userId, String username, String role) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + EXPIRE_SECONDS * 1000);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expire)
                .signWith(KEY)
                .compact();
    }

    /**
     * 解析 Token 载荷.
     *
     * @param token JWT Token 字符串
     * @return 载荷，解析失败返回 null
     */
    public static Claims parseToken(String token) {
        if (StrUtil.isBlank(token)) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 Token 中获取用户 ID.
     *
     * @param token JWT Token
     * @return 用户 ID，解析失败返回 null
     */
    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return Long.valueOf(claims.getSubject());
    }

    /**
     * 从 Token 中获取用户名.
     */
    public static String getUsername(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get("username", String.class);
    }

    /**
     * 从 Token 中获取角色.
     */
    public static String getRole(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get("role", String.class);
    }

    /**
     * 判断 Token 是否已过期.
     *
     * @return true 表示过期或无效
     */
    public static boolean isExpired(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return true;
        }
        return claims.getExpiration().before(new Date());
    }
}
