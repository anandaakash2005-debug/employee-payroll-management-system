
package com.akash.payroll.dto;

import com.akash.payroll.model.EmployeeBonus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record EmployeeBonusResponse(
        Long id,
        Long employeeId,
        String employeeName,
        BigDecimal amount,
        LocalDate bonusDate,
        String reason,
        String status,
        LocalDateTime createdAt,
        String createdBy
) {
    public static EmployeeBonusResponse from(EmployeeBonus bonus) {
        return new EmployeeBonusResponse(
                bonus.getId(),
                bonus.getEmployee().getId(),
                bonus.getEmployee().getName(),
                bonus.getAmount(),
                bonus.getBonusDate(),
                bonus.getReason(),
                bonus.getStatus().name(),
                bonus.getCreatedAt(),
                bonus.getCreatedBy()
        );
    }
}
