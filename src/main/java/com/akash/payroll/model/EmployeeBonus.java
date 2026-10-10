
package com.akash.payroll.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_bonuses")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeBonus {

    public enum BonusStatus {
        PENDING, PAID, CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "bonus_date", nullable = false)
    private LocalDate bonusDate;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BonusStatus status = BonusStatus.PENDING;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
