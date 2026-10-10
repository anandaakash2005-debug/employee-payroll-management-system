
package com.akash.payroll.controller;

import com.akash.payroll.dto.*;
import com.akash.payroll.service.PayrollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final PayrollService service;

    // Read operations: any authenticated user

    @GetMapping
    public ResponseEntity<List<EmployeeResponse>> getAllEmployees() {
        return ResponseEntity.ok(service.getAllEmployees());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/fulltime")
    public ResponseEntity<List<EmployeeResponse>> getFullTime() {
        return ResponseEntity.ok(service.getFullTimeEmployees());
    }

    @GetMapping("/parttime")
    public ResponseEntity<List<EmployeeResponse>> getPartTime() {
        return ResponseEntity.ok(service.getPartTimeEmployees());
    }

    @GetMapping("/department")
    public ResponseEntity<List<EmployeeResponse>> getByDepartment(
            @RequestParam String name) {
        return ResponseEntity.ok(service.getByDepartment(name));
    }

    @GetMapping("/search")
    public ResponseEntity<List<EmployeeResponse>> searchByName(
            @RequestParam String name) {
        return ResponseEntity.ok(service.searchByName(name));
    }

    @GetMapping("/summary")
    public ResponseEntity<PayrollSummaryResponse> getSummary() {
        return ResponseEntity.ok(service.getPayrollSummary());
    }

    // Create operations: administrators only

    @PostMapping("/fulltime")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmployeeResponse> addFullTime(
            @Valid @RequestBody FullTimeEmployeeRequest req) {
        EmployeeResponse created = service.addFullTimeEmployee(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/parttime")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmployeeResponse> addPartTime(
            @Valid @RequestBody PartTimeEmployeeRequest req) {
        EmployeeResponse created = service.addPartTimeEmployee(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // Update operations: administrators only

    @PutMapping("/{id}/fulltime")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmployeeResponse> updateFullTime(
            @PathVariable Long id,
            @Valid @RequestBody FullTimeEmployeeRequest req) {
        return ResponseEntity.ok(service.updateFullTimeEmployee(id, req));
    }

    @PutMapping("/{id}/parttime")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmployeeResponse> updatePartTime(
            @PathVariable Long id,
            @Valid @RequestBody PartTimeEmployeeRequest req) {
        return ResponseEntity.ok(service.updatePartTimeEmployee(id, req));
    }

    // Delete operations: administrators only

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        service.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }
}
