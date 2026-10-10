package com.akash.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * DTO for creating a PartTimeEmployee.
 */
@Data
public class PartTimeEmployeeRequest {

    @NotBlank(message = "Name is required")
    @jakarta.validation.constraints.Size(max = 255)
    private String name;

    @NotBlank(message = "Department is required")
    @jakarta.validation.constraints.Size(max = 255)
    private String department;

    @NotNull(message = "Hours worked is required")
    @Positive(message = "Hours worked must be positive")
    private Integer hoursWorked;

    @NotNull(message = "Hourly rate is required")
    @Positive(message = "Hourly rate must be positive")
    @jakarta.validation.constraints.Digits(integer = 10, fraction = 2)
    private java.math.BigDecimal hourlyRate;
    @com.fasterxml.jackson.annotation.JsonSetter("hourlyRate")
    public void setHourlyRate(java.math.BigDecimal value) { this.hourlyRate = value; }

    public void setHourlyRate(double value) { this.hourlyRate = java.math.BigDecimal.valueOf(value); }
}
