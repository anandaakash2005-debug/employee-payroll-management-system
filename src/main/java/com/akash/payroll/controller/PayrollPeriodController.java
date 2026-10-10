package com.akash.payroll.controller;

import com.akash.payroll.dto.*;
import com.akash.payroll.service.PayrollPeriodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/employees/{employeeId}/payroll")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PayrollPeriodController {
    private final PayrollPeriodService service;
    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public PayrollRecordResponse finalizePeriod(@PathVariable Long employeeId, @Valid @RequestBody PayrollPeriodRequest request) {
        return service.finalizePeriod(employeeId, request);
    }
    @GetMapping
    public List<PayrollRecordResponse> history(@PathVariable Long employeeId) { return service.history(employeeId); }
}
