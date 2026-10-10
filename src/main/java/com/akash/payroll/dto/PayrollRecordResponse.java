package com.akash.payroll.dto;

import com.akash.payroll.model.PayrollRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PayrollRecordResponse(Long id, Long employeeId, String employeeName, String department,
        String payBasis, LocalDate periodStart, LocalDate periodEnd, BigDecimal basePay,
        String status, LocalDateTime finalizedAt, String finalizedBy, String calculationDetails) {
    public static PayrollRecordResponse from(PayrollRecord r) {
        return new PayrollRecordResponse(r.getId(), r.getEmployee().getId(), r.getEmployeeName(),
                r.getDepartment(), r.getPayBasis(), r.getPeriodStart(), r.getPeriodEnd(), r.getBasePay(),
                "FINALIZED", r.getFinalizedAt(), r.getFinalizedBy(), r.getCalculationDetails());
    }
}
