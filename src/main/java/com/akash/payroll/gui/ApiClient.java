package com.akash.payroll.gui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class ApiClient {
    private static final String BASE_URL = resolveBaseUrl();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();

    private ApiClient() {
    }

    private static String resolveBaseUrl() {
        String configuredUrl = System.getProperty("payroll.api.url");
        if (configuredUrl == null || configuredUrl.isBlank()) {
            configuredUrl = System.getenv("PAYROLL_API_URL");
        }
        if (configuredUrl == null || configuredUrl.isBlank()) {
            configuredUrl = "http://localhost:8080";
        }

        String baseUrl = configuredUrl.trim();
        URI uri = URI.create(baseUrl);
        if (uri.getHost() == null
                || (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme()))
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
            throw new IllegalArgumentException(
                    "PAYROLL_API_URL must be an HTTP(S) backend origin without credentials, query, or path.");
        }
        return baseUrl.replaceAll("/+$", "");
    }

    public static String get(String path) throws ApiException {
        return send(path, "GET", null, true);
    }

    public static String post(String path, Object body) throws ApiException {
        return send(path, "POST", body, true);
    }

    public static String postPublic(String path, Object body) throws ApiException {
        return send(path, "POST", body, false);
    }

    public static String put(String path, Object body) throws ApiException {
        return send(path, "PUT", body, true);
    }

    public static String delete(String path) throws ApiException {
        return send(path, "DELETE", null, true);
    }

    private static String send(String path, String method, Object body, boolean requireAuth) throws ApiException {
        try {
            String requestBody = body == null ? null : MAPPER.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + path))
                    .header("Content-Type", "application/json")
                    .method(method, requestBody == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(requestBody));

            if (requireAuth && SessionManager.isAuthenticated()) {
                builder.header("Authorization", "Bearer " + SessionManager.getToken());
            }

            HttpRequest request = builder.build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            if (response.statusCode() >= 300) {
                throw new ApiException(response.statusCode(), extractErrorMessage(responseBody));
            }
            return responseBody;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(500, "Request interrupted");
        } catch (IOException e) {
            throw new ApiException(503, "Backend unavailable or network error");
        } catch (ApiException e) {
            throw e;
        }
    }

    private static String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "Request failed";
        }

        try {
            JsonNode node = MAPPER.readTree(responseBody);
            if (node.has("message") && !node.get("message").isNull()) {
                return node.get("message").asText();
            }
            if (node.has("error") && !node.get("error").isNull()) {
                return node.get("error").asText();
            }
            if (node.has("errors")) {
                JsonNode errors = node.get("errors");
                if (errors.isObject()) {
                    StringBuilder builder = new StringBuilder();
                    errors.fields().forEachRemaining(entry -> builder.append(entry.getKey()).append(": ").append(entry.getValue().asText()).append("\n"));
                    return builder.toString().trim();
                }
            }
        } catch (JsonProcessingException ignored) {
            return responseBody;
        }
        return responseBody;
    }
}
