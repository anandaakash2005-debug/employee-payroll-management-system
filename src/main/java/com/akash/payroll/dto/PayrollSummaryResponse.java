package com.akash.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary DTO returned by GET /api/employees/summary
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollSummaryResponse {

    private int totalEmployees;
    private int fullTimeCount;
    private int partTimeCount;
    private double totalPayroll;
    private double averageSalary;
}