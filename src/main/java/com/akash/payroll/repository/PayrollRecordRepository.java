package com.akash.payroll.repository;

import com.akash.payroll.model.PayrollRecord;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollRecordRepository extends JpaRepository<PayrollRecord, Long> {
    boolean existsByEmployee_IdAndPeriodEndGreaterThanEqual(Long id, LocalDate date);
    boolean existsByEmployee_IdAndPeriodStart(Long id, LocalDate start);
    List<PayrollRecord> findByEmployee_IdOrderByPeriodStartDesc(Long id);
}
