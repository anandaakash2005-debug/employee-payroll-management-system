package com.akash.payroll.repository;

import com.akash.payroll.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // Find all employees in a department (case-insensitive)
    List<Employee> findByDepartmentIgnoreCase(String department);

    // Search by name containing keyword (case-insensitive)
    List<Employee> findByNameContainingIgnoreCase(String keyword);

    // Find only full-time employees
    @Query("SELECT e FROM Employee e WHERE TYPE(e) = com.akash.payroll.model.FullTimeEmployee")
    List<Employee> findAllFullTimeEmployees();

    // Find only part-time employees
    @Query("SELECT e FROM Employee e WHERE TYPE(e) = com.akash.payroll.model.PartTimeEmployee")
    List<Employee> findAllPartTimeEmployees();

    // Check if employee name already exists
    boolean existsByNameIgnoreCase(String name);
}
