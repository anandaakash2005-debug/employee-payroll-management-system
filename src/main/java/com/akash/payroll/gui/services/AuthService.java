package com.akash.payroll.gui.services;

import com.akash.payroll.gui.ApiClient;
import com.akash.payroll.gui.ApiException;
import com.akash.payroll.gui.SessionManager;
import com.akash.payroll.gui.models.AuthResponse;
import com.akash.payroll.gui.models.LoginRequest;
import com.akash.payroll.gui.models.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AuthService {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public AuthResponse login(String username, String password) throws ApiException {
        String json = ApiClient.postPublic("/api/auth/login", new LoginRequest(username, password));
        try {
            AuthResponse response = MAPPER.readValue(json, AuthResponse.class);
            if (response.getToken() != null && !response.getToken().isBlank()) {
                SessionManager.login(response.getToken(), response.getUsername());
            }
            return response;
        } catch (Exception e) {
            throw new ApiException(500, "Unable to parse login response");
        }
    }

    public AuthResponse register(String username, String password) throws ApiException {
        String json = ApiClient.postPublic("/api/auth/register", new RegisterRequest(username, password));
        try {
            return MAPPER.readValue(json, AuthResponse.class);
        } catch (Exception e) {
            throw new ApiException(500, "Unable to parse registration response");
        }
    }

    public void logout() {
        SessionManager.logout();
    }
}
