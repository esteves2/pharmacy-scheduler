package com.farmacia.scheduler.api;

import com.farmacia.scheduler.api.dto.EmployeeDto;
import com.farmacia.scheduler.model.Employee;
import com.farmacia.scheduler.model.Role;
import com.farmacia.scheduler.repository.EmployeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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

    @GetMapping("/{id}")
    public EmployeeDto get(@PathVariable Long id) {
        Employee emp = employeeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee %d not found".formatted(id)));
        return new EmployeeDto(emp.getId(), emp.getName(), emp.getRole().name());
    }

    @PutMapping("/{id}")
    public EmployeeDto update(@PathVariable Long id, @RequestBody EmployeeDto request) {
        Employee emp = employeeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee %d not found".formatted(id)));
        emp.setName(request.name());
        emp.setRole(Role.valueOf(request.role()));
        Employee saved = employeeRepository.save(emp);
        return new EmployeeDto(saved.getId(), saved.getName(), saved.getRole().name());
    }
}
