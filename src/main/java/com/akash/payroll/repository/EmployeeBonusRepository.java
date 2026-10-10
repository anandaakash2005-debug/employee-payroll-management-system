
package com.akash.payroll.repository;

import com.akash.payroll.model.EmployeeBonus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeBonusRepository
        extends JpaRepository<EmployeeBonus, Long> {

    List<EmployeeBonus> findByEmployee_IdOrderByBonusDateDesc(
            Long employeeId);
}
