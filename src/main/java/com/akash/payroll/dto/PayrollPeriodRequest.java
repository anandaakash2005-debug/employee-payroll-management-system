package com.akash.payroll.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PayrollPeriodRequest(@NotNull LocalDate periodStart, @NotNull LocalDate periodEnd,
                                   @Size(max = 31) List<@Valid DailyHours> hours) {
    public record DailyHours(@NotNull LocalDate date,
                             @NotNull @DecimalMin("0.00") @DecimalMax("24.00")
                             @Digits(integer = 2, fraction = 2) BigDecimal hours) {}
}
