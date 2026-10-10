
package com.akash.payroll.repository;

import com.akash.payroll.model.SalaryIncrement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalaryIncrementRepository
        extends JpaRepository<SalaryIncrement, Long> {

    List<SalaryIncrement> findByEmployee_IdOrderByEffectiveDateDesc(
            Long employeeId);
}
