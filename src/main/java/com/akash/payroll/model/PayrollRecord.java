package com.akash.payroll.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Immutable finalized base-pay snapshot. No payment transfer or tax calculation. */
@Entity
@Table(name = "payroll_records", uniqueConstraints = @UniqueConstraint(name = "uk_payroll_employee_month",
        columnNames = {"employee_id", "period_start"}))
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PayrollRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;
    @Column(nullable = false, updatable = false)
    private String employeeName;
    @Column(nullable = false, updatable = false)
    private String department;
    @Column(nullable = false, updatable = false, length = 20)
    private String payBasis;
    @Column(nullable = false, updatable = false)
    private LocalDate periodStart;
    @Column(nullable = false, updatable = false)
    private LocalDate periodEnd;
    @Column(nullable = false, updatable = false, precision = 16, scale = 2)
    private BigDecimal basePay;
    @Column(nullable = false, updatable = false)
    private LocalDateTime finalizedAt;
    @Column(nullable = false, updatable = false)
    private String finalizedBy;
    @Lob @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String calculationDetails;

    public PayrollRecord(Employee employee, LocalDate start, LocalDate end, BigDecimal pay,
                         LocalDateTime at, String by, String details) {
        this.employee = employee;
        employeeName = employee.getName();
        department = employee.getDepartment();
        payBasis = employee instanceof FullTimeEmployee ? "MONTHLY" : "HOURLY";
        periodStart = start;
        periodEnd = end;
        basePay = pay;
        finalizedAt = at;
        finalizedBy = by;
        calculationDetails = details;
    }
}
