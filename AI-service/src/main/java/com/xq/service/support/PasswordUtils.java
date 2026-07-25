package com.xq.service.support;

import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.util.StringUtils;

public final class PasswordUtils {

    private PasswordUtils() {
    }

    public static String encode(String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            return rawPassword;
        }
        if (isBcrypt(rawPassword)) {
            return rawPassword;
        }
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    public static boolean matches(String rawPassword, String storedPassword) {
        if (rawPassword == null || storedPassword == null) {
            return false;
        }
        if (isBcrypt(storedPassword)) {
            return BCrypt.checkpw(rawPassword, storedPassword);
        }
        return rawPassword.equals(storedPassword);
    }

    public static boolean isBcrypt(String password) {
        return password != null
                && (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$"));
    }
}
