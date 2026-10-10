
package com.akash.payroll.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class EmployeeBonusRequest {

    @NotNull(message = "Bonus amount is required")
    @DecimalMin(value = "0.01", message = "Bonus must be greater than zero")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal amount;

    @NotNull(message = "Bonus date is required")
    private LocalDate bonusDate;

    @NotBlank(message = "Bonus reason is required")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
