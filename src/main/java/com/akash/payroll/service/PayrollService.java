
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

    // ── Add Employees ──────────────────────────────────────────────

    @Transactional
    public EmployeeResponse addFullTimeEmployee(
            FullTimeEmployeeRequest req) {

        FullTimeEmployee emp = new FullTimeEmployee(
                req.getName(),
                req.getDepartment(),
                req.getMonthlySalary()
        );

        return EmployeeResponse.from(repo.save(emp));
    }

    @Transactional
    public EmployeeResponse addPartTimeEmployee(
            PartTimeEmployeeRequest req) {

        PartTimeEmployee emp = new PartTimeEmployee(
                req.getName(),
                req.getDepartment(),
                req.getHoursWorked(),
                req.getHourlyRate()
        );

        return EmployeeResponse.from(repo.save(emp));
    }

    // ── Read Employees ─────────────────────────────────────────────

    public List<EmployeeResponse> getAllEmployees() {
        return repo.findAll()
                .stream()
                .map(EmployeeResponse::from)
                .toList();
    }

    public EmployeeResponse getById(Long id) {
        Employee emp = repo.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        return EmployeeResponse.from(emp);
    }

    public List<EmployeeResponse> getByDepartment(String department) {
        return repo.findByDepartmentIgnoreCase(department)
                .stream()
                .map(EmployeeResponse::from)
                .toList();
    }

    public List<EmployeeResponse> searchByName(String keyword) {
        return repo.findByNameContainingIgnoreCase(keyword)
                .stream()
                .map(EmployeeResponse::from)
                .toList();
    }

    public List<EmployeeResponse> getFullTimeEmployees() {
        return repo.findAllFullTimeEmployees()
                .stream()
                .map(EmployeeResponse::from)
                .toList();
    }

    public List<EmployeeResponse> getPartTimeEmployees() {
        return repo.findAllPartTimeEmployees()
                .stream()
                .map(EmployeeResponse::from)
                .toList();
    }

    // ── Update Full-Time Employee ──────────────────────────────────

    @Transactional
    public EmployeeResponse updateFullTimeEmployee(
            Long id, FullTimeEmployeeRequest req) {

        Employee emp = repo.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        if (!(emp instanceof FullTimeEmployee fte)) {
            throw new IllegalArgumentException(
                    "Employee ID " + id
                            + " is not a Full-Time employee."
            );
        }

        // Salary changes must use the audited compensation endpoint.
        if (Double.compare(
                req.getMonthlySalary(),
                fte.getMonthlySalary()
        ) != 0) {
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
    public EmployeeResponse updatePartTimeEmployee(
            Long id, PartTimeEmployeeRequest req) {

        Employee emp = repo.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        if (!(emp instanceof PartTimeEmployee pte)) {
            throw new IllegalArgumentException(
                    "Employee ID " + id
                            + " is not a Part-Time employee."
            );
        }

        // Hourly-rate changes must use the audited compensation endpoint.
        if (Double.compare(
                req.getHourlyRate(),
                pte.getHourlyRate()
        ) != 0) {
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
    public void deleteEmployee(Long id) {
        if (!repo.existsById(id)) {
            throw new EmployeeNotFoundException(id);
        }

        repo.deleteById(id);
    }

    // ── Payroll Summary ────────────────────────────────────────────

    public PayrollSummaryResponse getPayrollSummary() {

        List<Employee> all = repo.findAll();

        int total = all.size();
        int fullTimeCount = repo.findAllFullTimeEmployees().size();
        int partTimeCount = repo.findAllPartTimeEmployees().size();

        double totalPayroll = all.stream()
                .mapToDouble(Employee::calculateSalary)
                .sum();

        double avgSalary = total > 0
                ? totalPayroll / total
                : 0.0;

        return new PayrollSummaryResponse(
                total,
                fullTimeCount,
                partTimeCount,
                totalPayroll,
                avgSalary
        );
    }
}
