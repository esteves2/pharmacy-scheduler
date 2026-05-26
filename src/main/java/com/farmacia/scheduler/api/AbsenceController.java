package com.farmacia.scheduler.api;

import com.farmacia.scheduler.api.dto.AbsenceRequest;
import com.farmacia.scheduler.api.dto.AbsenceResponse;
import com.farmacia.scheduler.model.AbsenceType;
import com.farmacia.scheduler.model.EmployeeAbsence;
import com.farmacia.scheduler.repository.AbsenceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/absences")
public class AbsenceController {

    private final AbsenceRepository absenceRepository;

    public AbsenceController(AbsenceRepository absenceRepository) {
        this.absenceRepository = absenceRepository;
    }

    @GetMapping
    public List<AbsenceResponse> list(@RequestParam(required = false) Long employeeId) {
        List<EmployeeAbsence> absences = employeeId != null
                ? absenceRepository.findByEmployeeId(employeeId)
                : absenceRepository.findAll();
        return absences.stream().map(this::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<AbsenceResponse> create(@RequestBody AbsenceRequest request) {
        EmployeeAbsence absence = fromRequest(new EmployeeAbsence(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(absenceRepository.save(absence)));
    }

    @PutMapping("/{id}")
    public AbsenceResponse update(@PathVariable Long id, @RequestBody AbsenceRequest request) {
        EmployeeAbsence absence = absenceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Absence %d not found".formatted(id)));
        return toResponse(absenceRepository.save(fromRequest(absence, request)));
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

    private AbsenceResponse toResponse(EmployeeAbsence a) {
        return new AbsenceResponse(
                a.getId(), a.getEmployeeId(),
                a.getStartDate(), a.getEndDate(),
                a.getType().name(), a.getNote());
    }
}
