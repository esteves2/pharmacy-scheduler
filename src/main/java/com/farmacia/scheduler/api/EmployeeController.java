package com.farmacia.scheduler.api;

import com.farmacia.scheduler.api.dto.EmployeeDto;
import com.farmacia.scheduler.model.Employee;
import com.farmacia.scheduler.repository.EmployeeRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeRepository employeeRepository;

    public EmployeeController(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @GetMapping
    public List<EmployeeDto> list() {
        return employeeRepository.findAll().stream()
                .sorted(Comparator.comparing(Employee::getId))
                .map(e -> new EmployeeDto(e.getId(), e.getName(), e.getRole().name()))
                .toList();
    }
}
