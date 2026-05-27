package com.farmacia.scheduler.api;

import com.farmacia.scheduler.api.dto.AbsenceRequest;
import com.farmacia.scheduler.api.dto.AbsenceResponse;
import com.farmacia.scheduler.api.dto.EmployeeDto;
import com.farmacia.scheduler.model.AbsenceType;
import com.farmacia.scheduler.model.Employee;
import com.farmacia.scheduler.model.EmployeeAbsence;
import com.farmacia.scheduler.repository.AbsenceRepository;
import com.farmacia.scheduler.repository.EmployeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/absences")
public class AbsenceController {

    private final AbsenceRepository absenceRepository;
    private final EmployeeRepository employeeRepository;

    public AbsenceController(AbsenceRepository absenceRepository, EmployeeRepository employeeRepository) {
        this.absenceRepository = absenceRepository;
        this.employeeRepository = employeeRepository;
    }

    @GetMapping
    public List<AbsenceResponse> list(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        Map<Long, Employee> employees = employeeRepository.findAll().stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
        return absenceRepository.findOverlapping(from, to).stream()
                .map(a -> toResponse(a, employees))
                .toList();
    }

    @PostMapping
    public ResponseEntity<AbsenceResponse> create(@RequestBody AbsenceRequest request) {
        EmployeeAbsence absence = fromRequest(new EmployeeAbsence(), request);
        Map<Long, Employee> employees = employeeRepository.findAll().stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(absenceRepository.save(absence), employees));
    }

    @PutMapping("/{id}")
    public AbsenceResponse update(@PathVariable Long id, @RequestBody AbsenceRequest request) {
        EmployeeAbsence absence = absenceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Absence %d not found".formatted(id)));
        Map<Long, Employee> employees = employeeRepository.findAll().stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
        return toResponse(absenceRepository.save(fromRequest(absence, request)), employees);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!absenceRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Absence %d not found".formatted(id));
        }
        absenceRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private EmployeeAbsence fromRequest(EmployeeAbsence absence, AbsenceRequest request) {
        absence.setEmployeeId(request.employeeId());
        absence.setStartDate(request.startDate());
        absence.setEndDate(request.endDate());
        absence.setType(AbsenceType.valueOf(request.type()));
        absence.setNote(request.note());
        return absence;
    }

    private AbsenceResponse toResponse(EmployeeAbsence a, Map<Long, Employee> employees) {
        Employee emp = employees.get(a.getEmployeeId());
        EmployeeDto employeeDto = emp != null
                ? new EmployeeDto(emp.getId(), emp.getName(), emp.getRole().name())
                : new EmployeeDto(a.getEmployeeId(), "Unknown", null);
        return new AbsenceResponse(
                a.getId(), employeeDto,
                a.getStartDate(), a.getEndDate(),
                a.getType().name(), a.getNote());
    }
}
