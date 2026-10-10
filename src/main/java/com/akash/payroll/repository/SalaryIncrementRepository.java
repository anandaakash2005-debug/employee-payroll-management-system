
package com.akash.payroll.repository;

import com.akash.payroll.model.SalaryIncrement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalaryIncrementRepository
        extends JpaRepository<SalaryIncrement, Long> {

    List<SalaryIncrement> findByEmployee_IdOrderByEffectiveDateAsc(Long employeeId);

    @org.springframework.data.jpa.repository.Query("select distinct s.employee.id from SalaryIncrement s where s.status = :status and s.effectiveDate <= :today and s.employee.id > :after order by s.employee.id")
    List<Long> findDueEmployeeIds(@org.springframework.data.repository.query.Param("today") java.time.LocalDate today,
                                @org.springframework.data.repository.query.Param("after") Long after,
                                @org.springframework.data.repository.query.Param("status") SalaryIncrement.Status status,
                                org.springframework.data.domain.Pageable page);

    List<SalaryIncrement> findByEmployee_IdOrderByEffectiveDateDesc(
            Long employeeId);
}
