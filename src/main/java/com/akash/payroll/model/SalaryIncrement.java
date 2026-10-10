
package com.akash.payroll.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "salary_increments")
@Getter
@Setter
@NoArgsConstructor
public class SalaryIncrement {

    public enum PayBasis {
        MONTHLY, HOURLY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_basis", nullable = false)
    private PayBasis payBasis;

    @Column(name = "previous_rate", nullable = false,
            precision = 12, scale = 2)
    private BigDecimal previousRate;

    @Column(name = "new_rate", nullable = false,
            precision = 12, scale = 2)
    private BigDecimal newRate;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
