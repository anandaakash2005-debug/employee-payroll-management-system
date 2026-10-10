
package com.akash.payroll.service;

import com.akash.payroll.dto.EmployeeBonusRequest;
import com.akash.payroll.dto.EmployeeBonusResponse;
import com.akash.payroll.dto.SalaryIncrementRequest;
import com.akash.payroll.dto.SalaryIncrementResponse;
import com.akash.payroll.exception.EmployeeNotFoundException;
import com.akash.payroll.model.Employee;
import com.akash.payroll.model.EmployeeBonus;
import com.akash.payroll.model.FullTimeEmployee;
import com.akash.payroll.model.PartTimeEmployee;
import com.akash.payroll.model.SalaryIncrement;
import com.akash.payroll.repository.EmployeeBonusRepository;
import com.akash.payroll.repository.EmployeeRepository;
import com.akash.payroll.repository.SalaryIncrementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompensationService {

    private final EmployeeRepository employeeRepository;
    private final SalaryIncrementRepository salaryIncrementRepository;
    private final EmployeeBonusRepository employeeBonusRepository;

    @Transactional
    public SalaryIncrementResponse incrementSalary(
            Long employeeId, SalaryIncrementRequest request) {

        Employee employee = findEmployee(employeeId);

        BigDecimal previousRate;
        BigDecimal newRate;
        SalaryIncrement.PayBasis payBasis;

        if (employee instanceof FullTimeEmployee fullTime) {
            previousRate = BigDecimal.valueOf(fullTime.getMonthlySalary());
            payBasis = SalaryIncrement.PayBasis.MONTHLY;
        } else if (employee instanceof PartTimeEmployee partTime) {
            previousRate = BigDecimal.valueOf(partTime.getHourlyRate());
            payBasis = SalaryIncrement.PayBasis.HOURLY;
        } else {
            throw new IllegalArgumentException(
                    "Unsupported employee type for salary increment");
        }

        try {
            newRate = request.getNewRate().setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "New rate cannot have more than two decimal places");
        }

        if (newRate.compareTo(previousRate) <= 0) {
            throw new IllegalArgumentException(
                    "New rate must be greater than the current rate");
        }

        if (newRate.precision() > 12) {
            throw new IllegalArgumentException(
                    "New rate exceeds the maximum supported amount");
        }

        if (employee instanceof FullTimeEmployee fullTime) {
            fullTime.setMonthlySalary(newRate.doubleValue());
        } else if (employee instanceof PartTimeEmployee partTime) {
            partTime.setHourlyRate(newRate.doubleValue());
        }

        SalaryIncrement increment = new SalaryIncrement();
        increment.setEmployee(employee);
        increment.setPayBasis(payBasis);
        increment.setPreviousRate(previousRate.setScale(2, RoundingMode.HALF_UP));
        increment.setNewRate(newRate);
        increment.setEffectiveDate(request.getEffectiveDate());
        increment.setReason(request.getReason().trim());

        return SalaryIncrementResponse.from(
                salaryIncrementRepository.save(increment));
    }

    @Transactional
    public EmployeeBonusResponse addBonus(
            Long employeeId, EmployeeBonusRequest request) {

        Employee employee = findEmployee(employeeId);

        BigDecimal amount;
        try {
            amount = request.getAmount().setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Bonus amount cannot have more than two decimal places");
        }

        if (amount.precision() > 12) {
            throw new IllegalArgumentException(
                    "Bonus amount exceeds the maximum supported amount");
        }

        EmployeeBonus bonus = new EmployeeBonus();
        bonus.setEmployee(employee);
        bonus.setAmount(amount);
        bonus.setBonusDate(request.getBonusDate());
        bonus.setReason(request.getReason().trim());
        bonus.setStatus(EmployeeBonus.BonusStatus.PENDING);

        return EmployeeBonusResponse.from(
                employeeBonusRepository.save(bonus));
    }

    @Transactional(readOnly = true)
    public List<SalaryIncrementResponse> getSalaryHistory(Long employeeId) {
        findEmployee(employeeId);

        return salaryIncrementRepository
                .findByEmployee_IdOrderByEffectiveDateDesc(employeeId)
                .stream()
                .map(SalaryIncrementResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeeBonusResponse> getBonusHistory(Long employeeId) {
        findEmployee(employeeId);

        return employeeBonusRepository
                .findByEmployee_IdOrderByBonusDateDesc(employeeId)
                .stream()
                .map(EmployeeBonusResponse::from)
                .toList();
    }

    private Employee findEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new EmployeeNotFoundException(employeeId));
    }
}
