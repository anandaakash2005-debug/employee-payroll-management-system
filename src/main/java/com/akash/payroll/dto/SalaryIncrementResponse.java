
package com.akash.payroll.dto;

import com.akash.payroll.model.SalaryIncrement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SalaryIncrementResponse(
        Long id,
        Long employeeId,
        String employeeName,
        String payBasis,
        BigDecimal previousRate,
        BigDecimal newRate,
        LocalDate effectiveDate,
        String reason,
        LocalDateTime createdAt
) {
    public static SalaryIncrementResponse from(SalaryIncrement increment) {
        return new SalaryIncrementResponse(
                increment.getId(),
                increment.getEmployee().getId(),
                increment.getEmployee().getName(),
                increment.getPayBasis().name(),
                increment.getPreviousRate(),
                increment.getNewRate(),
                increment.getEffectiveDate(),
                increment.getReason(),
                increment.getCreatedAt()
        );
    }
}
