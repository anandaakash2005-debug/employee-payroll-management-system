package com.akash.payroll.service;

import com.akash.payroll.dto.*;
import com.akash.payroll.exception.*;
import com.akash.payroll.model.*;
import com.akash.payroll.repository.*;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompensationService {
    private final EmployeeRepository employees;
    private final SalaryIncrementRepository increments;
    private final EmployeeBonusRepository bonuses;
    private final PayrollRecordRepository payroll;
    private final Clock clock;
    private final Validator validator;

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public SalaryIncrementResponse incrementSalary(Long employeeId, SalaryIncrementRequest request) {
        validate(request);
        Employee employee = lockEmployee(employeeId);
        requireActive(employee);
        var history = increments.findByEmployee_IdOrderByEffectiveDateAsc(employeeId);
        var date = request.getEffectiveDate();
        if (history.stream().anyMatch(i -> i.getEffectiveDate().equals(date))) {
            throw new CompensationConflictException("An increment already occupies this effective date, including cancelled records");
        }
        if (payroll.existsByEmployee_IdAndPeriodEndGreaterThanEqual(employeeId, date)) {
            throw new CompensationConflictException("Effective date affects finalized payroll; an explicit adjustment workflow is required");
        }
        if (history.stream().anyMatch(i -> i.getStatus() == SalaryIncrement.Status.APPLIED
                && !i.getEffectiveDate().isBefore(date))) {
            throw new CompensationConflictException("Cannot insert before an applied change; an explicit correction workflow is required");
        }
        activateLocked(employee, history);
        // Activation may have made an overdue scheduled record immutable.
        if (history.stream().anyMatch(i -> i.getStatus() == SalaryIncrement.Status.APPLIED
                && !i.getEffectiveDate().isBefore(date))) {
            throw new CompensationConflictException("Cannot insert before a due or applied change");
        }
        BigDecimal previous = currentRate(employee);
        BigDecimal following = null;
        for (var item : history) {
            if (item.getStatus() == SalaryIncrement.Status.CANCELLED) continue;
            if (item.getEffectiveDate().isBefore(date)) previous = item.getNewRate();
            else { following = item.getNewRate(); break; }
        }
        BigDecimal rate = money(request.getNewRate());
        if (rate.compareTo(previous) <= 0 || (following != null && rate.compareTo(following) >= 0)) {
            throw new CompensationConflictException("New rate must exceed the preceding rate and be less than the next scheduled rate");
        }
        SalaryIncrement item = new SalaryIncrement();
        item.setEmployee(employee);
        item.setPayBasis(employee instanceof FullTimeEmployee ? SalaryIncrement.PayBasis.MONTHLY : SalaryIncrement.PayBasis.HOURLY);
        item.setPreviousRate(previous);
        item.setNewRate(rate);
        item.setEffectiveDate(date);
        item.setReason(request.getReason().trim());
        item.setCreatedAt(LocalDateTime.now(clock));
        item.setCreatedBy(actor());
        increments.save(item);
        history.add(item);
        history.sort(java.util.Comparator.comparing(SalaryIncrement::getEffectiveDate));
        rebaseScheduled(employee, history);
        activateLocked(employee, history);
        return SalaryIncrementResponse.from(item);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public SalaryIncrementResponse cancelIncrement(Long employeeId, Long incrementId) {
        Employee employee = lockEmployee(employeeId);
        var history = increments.findByEmployee_IdOrderByEffectiveDateAsc(employeeId);
        activateLocked(employee, history);
        var item = history.stream().filter(i -> i.getId().equals(incrementId)).findFirst()
                .orElseThrow(() -> new EmployeeNotFoundException(incrementId));
        if (item.getStatus() == SalaryIncrement.Status.CANCELLED) return SalaryIncrementResponse.from(item);
        if (item.getStatus() != SalaryIncrement.Status.SCHEDULED) {
            throw new CompensationConflictException("Only a future scheduled increment can be cancelled");
        }
        cancel(item, actor());
        rebaseScheduled(employee, history);
        return SalaryIncrementResponse.from(item);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivate(Long employeeId) {
        Employee employee = lockEmployee(employeeId);
        var history = increments.findByEmployee_IdOrderByEffectiveDateAsc(employeeId);
        activateLocked(employee, history);
        employee.setActive(false);
        history.stream().filter(i -> i.getStatus() == SalaryIncrement.Status.SCHEDULED)
                .forEach(i -> cancel(i, actor()));
    }

    /** Internal worker entry point; not exposed as an HTTP route. */
    @Transactional
    public void reconcile(Long employeeId) {
        Employee employee = lockEmployee(employeeId);
        activateLocked(employee, increments.findByEmployee_IdOrderByEffectiveDateAsc(employeeId));
    }

    private void activateLocked(Employee employee, List<SalaryIncrement> history) {
        LocalDate today = LocalDate.now(clock);
        for (var item : history) {
            if (item.getStatus() != SalaryIncrement.Status.SCHEDULED) continue;
            if (!employee.isActive()) { cancel(item, "SYSTEM:inactive"); continue; }
            if (item.getEffectiveDate().isAfter(today)) continue;
            item.setPreviousRate(currentRate(employee));
            if (employee instanceof FullTimeEmployee ft) ft.setMonthlySalary(item.getNewRate());
            else if (employee instanceof PartTimeEmployee pt) pt.setHourlyRate(item.getNewRate());
            item.setStatus(SalaryIncrement.Status.APPLIED);
            item.setAppliedAt(LocalDateTime.now(clock));
        }
    }

    private void rebaseScheduled(Employee employee, List<SalaryIncrement> history) {
        BigDecimal predecessor = currentRate(employee);
        for (var item : history) {
            if (item.getStatus() == SalaryIncrement.Status.SCHEDULED) {
                item.setPreviousRate(predecessor);
                predecessor = item.getNewRate();
            }
        }
    }

    private void cancel(SalaryIncrement item, String actor) {
        item.setStatus(SalaryIncrement.Status.CANCELLED);
        item.setCancelledAt(LocalDateTime.now(clock));
        item.setCancelledBy(actor);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public EmployeeBonusResponse addBonus(Long employeeId, EmployeeBonusRequest request) {
        validate(request);
        Employee employee = lockEmployee(employeeId);
        requireActive(employee);
        if (request.getBonusDate().isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("Bonus date cannot be in the past");
        }
        BigDecimal amount = money(request.getAmount());
        String reason = request.getReason().trim();
        if (bonuses.existsByEmployee_IdAndBonusDateAndAmountAndReason(employeeId, request.getBonusDate(), amount, reason)) {
            throw new CompensationConflictException("An identical bonus already exists");
        }
        EmployeeBonus bonus = new EmployeeBonus();
        bonus.setEmployee(employee);
        bonus.setAmount(amount);
        bonus.setBonusDate(request.getBonusDate());
        bonus.setReason(reason);
        bonus.setStatus(EmployeeBonus.BonusStatus.PENDING);
        bonus.setCreatedAt(LocalDateTime.now(clock));
        bonus.setCreatedBy(actor());
        return EmployeeBonusResponse.from(bonuses.save(bonus));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<SalaryIncrementResponse> getSalaryHistory(Long employeeId) {
        employees.findById(employeeId).orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        return increments.findByEmployee_IdOrderByEffectiveDateDesc(employeeId).stream().map(SalaryIncrementResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<EmployeeBonusResponse> getBonusHistory(Long employeeId) {
        employees.findById(employeeId).orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        return bonuses.findByEmployee_IdOrderByBonusDateDesc(employeeId).stream().map(EmployeeBonusResponse::from).toList();
    }

    public static BigDecimal currentRate(Employee employee) {
        if (employee instanceof FullTimeEmployee ft) return ft.getMonthlySalary();
        if (employee instanceof PartTimeEmployee pt) return pt.getHourlyRate();
        throw new IllegalArgumentException("Unsupported employee type");
    }

    public static BigDecimal money(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Amount must be positive with at most two decimal places");
        }
        BigDecimal value = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (value.precision() > 12) throw new IllegalArgumentException("Amount exceeds the supported maximum");
        return value;
    }

    private void validate(Object request) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new IllegalArgumentException(violations.iterator().next().getMessage());
    }

    private Employee lockEmployee(Long id) {
        return employees.findLockedById(id).orElseThrow(() -> new EmployeeNotFoundException(id));
    }

    private void requireActive(Employee employee) {
        if (!employee.isActive()) throw new CompensationConflictException("Employee is inactive");
    }

    public static String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        }
        return authentication.getName();
    }
}
