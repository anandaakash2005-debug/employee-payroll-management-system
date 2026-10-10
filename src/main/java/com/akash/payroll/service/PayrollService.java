
package com.akash.payroll.service;

import com.akash.payroll.dto.*;
import com.akash.payroll.exception.EmployeeNotFoundException;
import com.akash.payroll.model.Employee;
import com.akash.payroll.model.FullTimeEmployee;
import com.akash.payroll.model.PartTimeEmployee;
import com.akash.payroll.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic layer backed by MySQL via JPA.
 */
@Service
@RequiredArgsConstructor
public class PayrollService {

    private final EmployeeRepository repo;
    private final CompensationService compensation;

    // ── Add Employees ──────────────────────────────────────────────

    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public EmployeeResponse addFullTimeEmployee(
            FullTimeEmployeeRequest req) {

        FullTimeEmployee emp = new FullTimeEmployee(
                req.getName(),
                req.getDepartment(),
                CompensationService.money(req.getMonthlySalary())
        );

        return EmployeeResponse.from(repo.save(emp));
    }

    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public EmployeeResponse addPartTimeEmployee(
            PartTimeEmployeeRequest req) {

        PartTimeEmployee emp = new PartTimeEmployee(
                req.getName(),
                req.getDepartment(),
                req.getHoursWorked(),
                CompensationService.money(req.getHourlyRate())
        );

        return EmployeeResponse.from(repo.save(emp));
    }

    // ── Read Employees ─────────────────────────────────────────────

    public List<EmployeeResponse> getAllEmployees() {
        return repo.findAll()
                .stream()
                .map(this::currentResponse)
                .toList();
    }

    public EmployeeResponse getById(Long id) {
        compensation.reconcile(id);
        Employee emp = repo.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        return EmployeeResponse.from(emp);
    }

    public List<EmployeeResponse> getByDepartment(String department) {
        return repo.findByDepartmentIgnoreCase(department)
                .stream()
                .map(this::currentResponse)
                .toList();
    }

    public List<EmployeeResponse> searchByName(String keyword) {
        return repo.findByNameContainingIgnoreCase(keyword)
                .stream()
                .map(this::currentResponse)
                .toList();
    }

    public List<EmployeeResponse> getFullTimeEmployees() {
        return repo.findAllFullTimeEmployees()
                .stream()
                .map(this::currentResponse)
                .toList();
    }

    public List<EmployeeResponse> getPartTimeEmployees() {
        return repo.findAllPartTimeEmployees()
                .stream()
                .map(this::currentResponse)
                .toList();
    }

    // ── Update Full-Time Employee ──────────────────────────────────

    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public EmployeeResponse updateFullTimeEmployee(
            Long id, FullTimeEmployeeRequest req) {

        Employee emp = repo.findLockedById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        if (!(emp instanceof FullTimeEmployee fte)) {
            throw new IllegalArgumentException(
                    "Employee ID " + id
                            + " is not a Full-Time employee."
            );
        }

        // Salary changes must use the audited compensation endpoint.
        if (req.getMonthlySalary().compareTo(fte.getMonthlySalary()) != 0) {
            throw new IllegalArgumentException(
                    "Use the salary-increments endpoint to change salary "
                            + "so the change is recorded in history."
            );
        }

        fte.setName(req.getName());
        fte.setDepartment(req.getDepartment());

        return EmployeeResponse.from(repo.save(fte));
    }

    // ── Update Part-Time Employee ───────────────────────────────────

    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public EmployeeResponse updatePartTimeEmployee(
            Long id, PartTimeEmployeeRequest req) {

        Employee emp = repo.findLockedById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        if (!(emp instanceof PartTimeEmployee pte)) {
            throw new IllegalArgumentException(
                    "Employee ID " + id
                            + " is not a Part-Time employee."
            );
        }

        // Hourly-rate changes must use the audited compensation endpoint.
        if (req.getHourlyRate().compareTo(pte.getHourlyRate()) != 0) {
            throw new IllegalArgumentException(
                    "Use the salary-increments endpoint to change hourly rate "
                            + "so the change is recorded in history."
            );
        }

        pte.setName(req.getName());
        pte.setDepartment(req.getDepartment());
        pte.setHoursWorked(req.getHoursWorked());

        return EmployeeResponse.from(repo.save(pte));
    }

    // ── Delete Employee ────────────────────────────────────────────

    @Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public void deleteEmployee(Long id) {
        if (repo.findLockedById(id).isEmpty()) {
            throw new EmployeeNotFoundException(id);
        }

        repo.deleteById(id);
    }

    // ── Payroll Summary ────────────────────────────────────────────

    public PayrollSummaryResponse getPayrollSummary() {

        List<Employee> all = repo.findAll();

        for (Employee employee : all) compensation.reconcile(employee.getId());
        all = repo.findAll().stream().filter(Employee::isActive).toList();
        int total = all.size();
        int fullTimeCount = (int) all.stream().filter(FullTimeEmployee.class::isInstance).count();
        int partTimeCount = total - fullTimeCount;
        java.math.BigDecimal totalPayroll = all.stream().map(Employee::calculatePay)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal avgSalary = total == 0 ? java.math.BigDecimal.ZERO
                : totalPayroll.divide(java.math.BigDecimal.valueOf(total), 2, java.math.RoundingMode.HALF_UP);

        return new PayrollSummaryResponse(
                total,
                fullTimeCount,
                partTimeCount,
                totalPayroll.doubleValue(),
                avgSalary.doubleValue()
        );
    }
    private EmployeeResponse currentResponse(Employee employee) {
        compensation.reconcile(employee.getId());
        return EmployeeResponse.from(repo.findById(employee.getId())
                .orElseThrow(() -> new EmployeeNotFoundException(employee.getId())));
    }
}
