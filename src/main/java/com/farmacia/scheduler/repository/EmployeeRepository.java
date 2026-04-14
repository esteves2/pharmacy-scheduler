package com.farmacia.scheduler.repository;

import com.farmacia.scheduler.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
}