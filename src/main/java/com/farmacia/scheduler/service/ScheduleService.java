package com.farmacia.scheduler.service;

import com.farmacia.scheduler.engine.ScheduleEngine;
import com.farmacia.scheduler.engine.model.DayPlan;
import com.farmacia.scheduler.engine.model.SlotAssignment;
import com.farmacia.scheduler.engine.model.WeekResult;
import com.farmacia.scheduler.model.*;
import com.farmacia.scheduler.repository.*;
import com.farmacia.scheduler.service.exception.ScheduleAlreadyExistsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ScheduleService {

    private final EmployeeRepository employeeRepository;
    private final AbsenceRepository absenceRepository;
    private final HolidayRepository holidayRepository;
    private final ScheduleWeekRepository scheduleWeekRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final HolidayGeneratorService holidayGeneratorService;
    private final ScheduleEngine scheduleEngine;

    public ScheduleService(
            EmployeeRepository employeeRepository,
            AbsenceRepository absenceRepository,
            HolidayRepository holidayRepository,
            ScheduleWeekRepository scheduleWeekRepository,
            ShiftAssignmentRepository shiftAssignmentRepository,
            HolidayGeneratorService holidayGeneratorService,
            ScheduleEngine scheduleEngine) {
        this.employeeRepository = employeeRepository;
        this.absenceRepository = absenceRepository;
        this.holidayRepository = holidayRepository;
        this.scheduleWeekRepository = scheduleWeekRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.holidayGeneratorService = holidayGeneratorService;
        this.scheduleEngine = scheduleEngine;
    }

    @Transactional
    public WeekResult generate(int isoYear, int isoWeek) {
        if (scheduleWeekRepository.findByIsoYearAndIsoWeek(isoYear, isoWeek).isPresent()) {
            throw new ScheduleAlreadyExistsException(
                    "Schedule already exists for ISO year %d week %d".formatted(isoYear, isoWeek));
        }

        // Jan 4 is always in ISO week 1, so this anchors correctly regardless of year boundary
        LocalDate weekStart = LocalDate.of(isoYear, 1, 4)
                .with(WeekFields.ISO.weekOfWeekBasedYear(), isoWeek)
                .with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);

        holidayGeneratorService.ensureGenerated(weekStart.getYear());
        if (weekEnd.getYear() != weekStart.getYear()) {
            holidayGeneratorService.ensureGenerated(weekEnd.getYear());
        }

        List<Employee> employees = employeeRepository.findAll();
        List<EmployeeAbsence> absences = absenceRepository.findOverlapping(weekStart, weekEnd);
        Set<LocalDate> holidays = holidayRepository.findBetween(weekStart, weekEnd)
                .stream()
                .map(PublicHoliday::getDate)
                .collect(Collectors.toSet());
        List<ShiftAssignment> priorAssignments =
                shiftAssignmentRepository.findByDateRange(weekStart.minusWeeks(4), weekStart.minusDays(1));

        Map<Long, LocalDate> effectiveLastWeekendWorked = priorAssignments.stream()
                .filter(a -> a.getDate().getDayOfWeek() == DayOfWeek.SATURDAY)
                .collect(Collectors.toMap(
                        ShiftAssignment::getEmployeeId,
                        ShiftAssignment::getDate,
                        (a, b) -> !a.isBefore(b) ? a : b));

        WeekResult result = scheduleEngine.generate(
                isoYear, isoWeek, weekStart, employees, absences, holidays, priorAssignments,
                effectiveLastWeekendWorked);

        ScheduleWeek week = new ScheduleWeek();
        week.setIsoYear(isoYear);
        week.setIsoWeek(isoWeek);
        week.setStatus(WeekStatus.DRAFT);
        week.setGeneratedAt(LocalDateTime.now());
        scheduleWeekRepository.save(week);

        List<ShiftAssignment> assignments = new ArrayList<>();
        for (DayPlan day : result.getDays()) {
            for (SlotAssignment slot : day.getAssignments()) {
                ShiftAssignment entity = getShiftAssignment(slot, week);
                assignments.add(entity);
            }
        }
        shiftAssignmentRepository.saveAll(assignments);

        return result;
    }

    private static ShiftAssignment getShiftAssignment(SlotAssignment slot, ScheduleWeek week) {
        ShiftAssignment entity = new ShiftAssignment();
        entity.setScheduleWeekId(week.getId());
        entity.setEmployeeId(slot.getEmployeeId());
        entity.setDate(slot.getDate());
        entity.setStartTime(slot.getStartTime().toString());
        entity.setEndTime(slot.getEndTime().toString());
        entity.setBreakStart(slot.getBreakStart() != null ? slot.getBreakStart().toString() : null);
        entity.setBreakEnd(slot.getBreakEnd() != null ? slot.getBreakEnd().toString() : null);
        return entity;
    }
}
