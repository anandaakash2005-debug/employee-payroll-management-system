package com.akash.payroll.gui.models;

public class AuthResponse {
    private String token;
    private String username;
    private String role;
    private String message;

    public String getToken() {
        return token;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public String getMessage() {
        return message;
    }
}
