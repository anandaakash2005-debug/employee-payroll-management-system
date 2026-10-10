package com.akash.payroll.service;

import com.akash.payroll.dto.*;
import com.akash.payroll.exception.*;
import com.akash.payroll.model.*;
import com.akash.payroll.repository.*;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PayrollPeriodService {
    private final EmployeeRepository employees;
    private final SalaryIncrementRepository increments;
    private final PayrollRecordRepository records;
    private final Clock clock;
    private final Validator validator;

    @Transactional
    public PayrollRecordResponse finalizePeriod(Long employeeId, PayrollPeriodRequest request) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new IllegalArgumentException(violations.iterator().next().getMessage());
        LocalDate start = request.periodStart(), end = request.periodEnd();
        if (start.getDayOfMonth() != 1 || !end.equals(start.withDayOfMonth(start.lengthOfMonth()))
                || !end.isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("Payroll must cover exactly one completed calendar month");
        }
        Employee employee = employees.findLockedById(employeeId).orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        if (records.existsByEmployee_IdAndPeriodStart(employeeId, start)) {
            throw new CompensationConflictException("Payroll is already finalized for this employee and month");
        }
        var history = increments.findByEmployee_IdOrderByEffectiveDateAsc(employeeId).stream()
                .filter(i -> i.getStatus() != SalaryIncrement.Status.CANCELLED).toList();
        // The first history record carries the original rate, including legacy imports.
        BigDecimal baseline = history.isEmpty() ? CompensationService.currentRate(employee) : history.get(0).getPreviousRate();
        Map<LocalDate, BigDecimal> hours = new HashMap<>();
        if (employee instanceof PartTimeEmployee) {
            if (request.hours() == null || request.hours().size() != start.lengthOfMonth()) {
                throw new IllegalArgumentException("Supply explicit hours for every day of the month, including zero-hour days");
            }
            for (var day : request.hours()) {
                if (day.date().isBefore(start) || day.date().isAfter(end) || hours.put(day.date(), day.hours()) != null) {
                    throw new IllegalArgumentException("Hours must contain unique dates inside the payroll month");
                }
            }
        } else if (request.hours() != null && !request.hours().isEmpty()) {
            throw new IllegalArgumentException("Hours are only supported for hourly employees");
        }
        BigDecimal total = BigDecimal.ZERO;
        StringBuilder details = new StringBuilder("v1; calendar-day proration; HALF_UP at monthly total; bonuses excluded\n");
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            BigDecimal rate = baseline;
            for (var item : history) {
                if (!item.getEffectiveDate().isAfter(date)) rate = item.getNewRate();
                else break;
            }
            if (rate == null || rate.signum() <= 0) throw new IllegalArgumentException("Missing valid compensation");
            BigDecimal units = employee instanceof PartTimeEmployee ? hours.get(date) : BigDecimal.ONE;
            // Accumulate monthly rates first, then divide once to avoid daily rounding drift.
            total = total.add(rate.multiply(units));
            details.append(date).append(": rate=").append(rate).append("; units=").append(units).append('\n');
        }
        if (employee instanceof FullTimeEmployee) total = total.divide(BigDecimal.valueOf(start.lengthOfMonth()), 2, RoundingMode.HALF_UP);
        else total = total.setScale(2, RoundingMode.HALF_UP);
        if (total.precision() > 16) throw new IllegalArgumentException("Payroll amount exceeds the supported maximum");
        PayrollRecord record = new PayrollRecord(employee, start, end, total, LocalDateTime.now(clock),
                CompensationService.actor(), details.toString());
        return PayrollRecordResponse.from(records.save(record));
    }

    @Transactional(readOnly = true)
    public List<PayrollRecordResponse> history(Long employeeId) {
        employees.findById(employeeId).orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        return records.findByEmployee_IdOrderByPeriodStartDesc(employeeId).stream().map(PayrollRecordResponse::from).toList();
    }
}
