
package com.akash.payroll.controller;

import com.akash.payroll.dto.EmployeeBonusRequest;
import com.akash.payroll.dto.EmployeeBonusResponse;
import com.akash.payroll.dto.SalaryIncrementRequest;
import com.akash.payroll.dto.SalaryIncrementResponse;
import com.akash.payroll.service.CompensationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees/{employeeId}")
@RequiredArgsConstructor
public class CompensationController {

    private final CompensationService compensationService;

    @DeleteMapping("/salary-increments/{incrementId}")
    @PreAuthorize("hasRole('ADMIN')")
    public SalaryIncrementResponse cancelIncrement(@PathVariable Long employeeId, @PathVariable Long incrementId) {
        return compensationService.cancelIncrement(employeeId, incrementId);
    }

    @PutMapping("/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(@PathVariable Long employeeId) {
        compensationService.deactivate(employeeId);
    }

    @PutMapping("/salary-increments")
    @PreAuthorize("hasRole('ADMIN')")
    public SalaryIncrementResponse incrementSalary(
            @PathVariable Long employeeId,
            @Valid @RequestBody SalaryIncrementRequest request) {
        return compensationService.incrementSalary(employeeId, request);
    }

    @GetMapping("/salary-increments")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SalaryIncrementResponse> getSalaryHistory(
            @PathVariable Long employeeId) {
        return compensationService.getSalaryHistory(employeeId);
    }

    @PostMapping("/bonuses")
    @PreAuthorize("hasRole('ADMIN')")
    public EmployeeBonusResponse addBonus(
            @PathVariable Long employeeId,
            @Valid @RequestBody EmployeeBonusRequest request) {
        return compensationService.addBonus(employeeId, request);
    }

    @GetMapping("/bonuses")
    @PreAuthorize("hasRole('ADMIN')")
    public List<EmployeeBonusResponse> getBonusHistory(
            @PathVariable Long employeeId) {
        return compensationService.getBonusHistory(employeeId);
    }
}
