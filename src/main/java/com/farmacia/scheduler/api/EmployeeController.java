package com.farmacia.scheduler.api;

import com.farmacia.scheduler.api.dto.EmployeeDetailDto;
import com.farmacia.scheduler.model.AbsenceType;
import com.farmacia.scheduler.model.Employee;
import com.farmacia.scheduler.model.EmployeeAbsence;
import com.farmacia.scheduler.model.Role;
import com.farmacia.scheduler.repository.AbsenceRepository;
import com.farmacia.scheduler.repository.EmployeeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    static final int HOLIDAY_ENTITLEMENT = 22;

    private final EmployeeRepository employeeRepository;
    private final AbsenceRepository absenceRepository;

    public EmployeeController(EmployeeRepository employeeRepository,
                             AbsenceRepository absenceRepository) {
        this.employeeRepository = employeeRepository;
        this.absenceRepository = absenceRepository;
    }

    @GetMapping
    public List<EmployeeDetailDto> list() {
        Map<Long, Integer> holidaysUsedMap = computeHolidaysUsedThisYear();
        return employeeRepository.findAll().stream()
                .sorted(Comparator.comparing(Employee::getId))
                .map(emp -> toDetail(emp, holidaysUsedMap.getOrDefault(emp.getId(), 0)))
                .toList();
    }

    @GetMapping("/{id}")
    public EmployeeDetailDto get(@PathVariable Long id) {
        Employee emp = employeeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee %d not found".formatted(id)));
        return toDetail(emp, computeHolidaysUsedThisYear().getOrDefault(id, 0));
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
        emp.setBirthday(request.birthday() != null ? LocalDate.parse(request.birthday()) : null);
        Employee saved = employeeRepository.save(emp);
        return toDetail(saved, computeHolidaysUsedThisYear().getOrDefault(id, 0));
    }

    private Map<Long, Integer> computeHolidaysUsedThisYear() {
        int year = LocalDate.now().getYear();
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd   = LocalDate.of(year, 12, 31);
        List<EmployeeAbsence> ferias = absenceRepository.findByTypeAndDateRange(
                AbsenceType.FERIAS, yearStart, yearEnd);

        Map<Long, Integer> used = new HashMap<>();
        for (EmployeeAbsence absence : ferias) {
            LocalDate from = absence.getStartDate().isBefore(yearStart) ? yearStart : absence.getStartDate();
            LocalDate to   = absence.getEndDate().isAfter(yearEnd) ? yearEnd : absence.getEndDate();
            if (!from.isAfter(to)) {
                int days = (int) (ChronoUnit.DAYS.between(from, to) + 1);
                used.merge(absence.getEmployeeId(), days, Integer::sum);
            }
        }
        return used;
    }

    private EmployeeDetailDto toDetail(Employee emp, int holidaysUsed) {
        int remaining = Math.max(0, HOLIDAY_ENTITLEMENT - holidaysUsed);
        return new EmployeeDetailDto(
                emp.getId(),
                emp.getName(),
                emp.getRole().name(),
                emp.getPhone(),
                emp.getEmail(),
                emp.getNotes(),
                emp.getBirthday() != null ? emp.getBirthday().toString() : null,
                holidaysUsed,
                remaining);
    }
}
