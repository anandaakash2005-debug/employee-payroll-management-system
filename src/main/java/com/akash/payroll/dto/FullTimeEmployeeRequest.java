package com.akash.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * DTO for creating a FullTimeEmployee.
 * Keeps the API contract separate from the DB entity.
 */
@Data
public class FullTimeEmployeeRequest {

    @NotBlank(message = "Name is required")
    @jakarta.validation.constraints.Size(max = 255)
    private String name;

    @NotBlank(message = "Department is required")
    @jakarta.validation.constraints.Size(max = 255)
    private String department;

    @NotNull(message = "Monthly salary is required")
    @Positive(message = "Monthly salary must be positive")
    @jakarta.validation.constraints.Digits(integer = 10, fraction = 2)
    private java.math.BigDecimal monthlySalary;
    @com.fasterxml.jackson.annotation.JsonSetter("monthlySalary")
    public void setMonthlySalary(java.math.BigDecimal value) { this.monthlySalary = value; }

    public void setMonthlySalary(double value) { this.monthlySalary = java.math.BigDecimal.valueOf(value); }
}
