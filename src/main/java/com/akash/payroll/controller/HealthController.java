package com.akash.payroll.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final javax.sql.DataSource dataSource;

    public HealthController(javax.sql.DataSource dataSource) { this.dataSource = dataSource; }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> ready() {
        try (var connection = dataSource.getConnection()) {
            if (connection.isValid(2)) return ResponseEntity.ok(Map.of("status", "UP"));
        } catch (java.sql.SQLException ignored) {
            // Deliberately keep connection information out of the public health response.
        }
        return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "application", "Employee Payroll API"
        ));
    }
}
