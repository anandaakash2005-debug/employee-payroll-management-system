package com.akash.payroll.gui;

public final class SessionManager {
    private static String token;
    private static String currentUser;

    private SessionManager() {
    }

    public static void login(String authToken, String username) {
        token = authToken;
        currentUser = username;
    }

    public static void logout() {
        token = null;
        currentUser = null;
    }

    public static String getToken() {
        return token;
    }

    public static String getCurrentUser() {
        return currentUser;
    }

    public static boolean isAuthenticated() {
        return token != null && !token.isBlank();
    }
}
