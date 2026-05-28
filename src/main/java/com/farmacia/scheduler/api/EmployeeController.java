package com.farmacia.scheduler.api;

import com.farmacia.scheduler.api.dto.EmployeeDetailDto;
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
    public List<EmployeeDetailDto> list() {
        return employeeRepository.findAll().stream()
                .sorted(Comparator.comparing(Employee::getId))
                .map(this::toDetail)
                .toList();
    }

    @GetMapping("/{id}")
    public EmployeeDetailDto get(@PathVariable Long id) {
        return toDetail(employeeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee %d not found".formatted(id))));
    }

    @PutMapping("/{id}")
    public EmployeeDetailDto update(@PathVariable Long id, @RequestBody EmployeeDetailDto request) {
        Employee emp = employeeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee %d not found".formatted(id)));
        emp.setName(request.name());
        emp.setRole(Role.valueOf(request.role()));
        emp.setPhone(request.phone());
        emp.setEmail(request.email());
        emp.setNotes(request.notes());
        return toDetail(employeeRepository.save(emp));
    }

    private EmployeeDetailDto toDetail(Employee emp) {
        return new EmployeeDetailDto(
                emp.getId(), emp.getName(), emp.getRole().name(),
                emp.getPhone(), emp.getEmail(), emp.getNotes());
    }
}
