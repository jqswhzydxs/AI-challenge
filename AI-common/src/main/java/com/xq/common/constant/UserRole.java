package com.xq.common.constant;

/**
 * User role constants.
 */
public final class UserRole {

    public static final String ADMIN = "ADMIN";
    public static final String USER = "USER";

    /** Legacy admin role code kept for compatibility with old demo data. */
    public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    /** Legacy production role code, now treated as USER. */
    public static final String PRODUCTION_DISPATCHER = "PRODUCTION_DISPATCHER";
    /** Legacy energy role code, now treated as USER. */
    public static final String ENERGY_MANAGER = "ENERGY_MANAGER";

    private UserRole() {
    }

    public static boolean isAdmin(String role) {
        return ADMIN.equals(role) || SYSTEM_ADMIN.equals(role);
    }

    public static boolean isUser(String role) {
        return USER.equals(role) || PRODUCTION_DISPATCHER.equals(role) || ENERGY_MANAGER.equals(role);
    }

    public static String canonicalRole(String role) {
        return isAdmin(role) ? ADMIN : USER;
    }
}
