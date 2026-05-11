package com.farmacia.scheduler.service;

import com.farmacia.scheduler.api.dto.*;
import com.farmacia.scheduler.engine.ScheduleEngine;
import com.farmacia.scheduler.engine.ScheduleValidator;
import com.farmacia.scheduler.engine.ShiftTemplates;
import com.farmacia.scheduler.engine.model.*;
import com.farmacia.scheduler.model.*;
import com.farmacia.scheduler.repository.*;
import com.farmacia.scheduler.service.exception.ScheduleAlreadyExistsException;
import com.farmacia.scheduler.service.exception.ScheduleAlreadyPublishedException;
import com.farmacia.scheduler.service.exception.ScheduleHasValidationErrorsException;
import com.farmacia.scheduler.service.exception.ScheduleNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.WeekFields;
import java.util.*;
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
    public WeekResponse generate(int isoYear, int isoWeek) {
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
                assignments.add(getShiftAssignment(slot, week));
            }
        }
        shiftAssignmentRepository.saveAll(assignments);

        return buildWeekResponse(week);
    }

    public WeekResponse getWeek(int isoYear, int isoWeek) {
        ScheduleWeek week = scheduleWeekRepository.findByIsoYearAndIsoWeek(isoYear, isoWeek)
                .orElseThrow(() -> new ScheduleNotFoundException(
                        "No schedule found for ISO year %d week %d".formatted(isoYear, isoWeek)));
        return buildWeekResponse(week);
    }

    @Transactional
    public WeekResponse save(int isoYear, int isoWeek, WeekWriteRequest request) {
        ScheduleWeek week = scheduleWeekRepository.findByIsoYearAndIsoWeek(isoYear, isoWeek)
                .orElseThrow(() -> new ScheduleNotFoundException(
                        "No schedule found for ISO year %d week %d".formatted(isoYear, isoWeek)));

        shiftAssignmentRepository.deleteByScheduleWeekId(week.getId());

        List<ShiftAssignment> assignments = request.getAssignments().stream()
                .map(req -> toShiftAssignment(req, week))
                .collect(Collectors.toList());
        shiftAssignmentRepository.saveAll(assignments);

        week.setLastEditedAt(LocalDateTime.now());
        ScheduleWeek saved = scheduleWeekRepository.save(week);
        return buildWeekResponse(saved);
    }

    @Transactional
    public WeekResponse publish(int isoYear, int isoWeek) {
        ScheduleWeek week = scheduleWeekRepository.findByIsoYearAndIsoWeek(isoYear, isoWeek)
                .orElseThrow(() -> new ScheduleNotFoundException(
                        "No schedule found for ISO year %d week %d".formatted(isoYear, isoWeek)));

        if (week.getStatus() == WeekStatus.PUBLISHED) {
            throw new ScheduleAlreadyPublishedException(
                    "Schedule for ISO year %d week %d is already published".formatted(isoYear, isoWeek));
        }

        LocalDate weekStart = LocalDate.of(isoYear, 1, 4)
                .with(WeekFields.ISO.weekOfWeekBasedYear(), isoWeek)
                .with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);

        Set<LocalDate> holidays = holidayRepository.findBetween(weekStart, weekEnd)
                .stream()
                .map(PublicHoliday::getDate)
                .collect(Collectors.toSet());

        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByScheduleWeekId(week.getId());

        Map<Long, Employee> employeeMap = employeeRepository.findAll()
                .stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        Map<Long, String> idToName = employeeMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getName()));

        Map<LocalDate, DayPlan> plansByDate = new TreeMap<>();
        for (ShiftAssignment sa : assignments) {
            LocalDate date = sa.getDate();
            DayPlan plan = plansByDate.computeIfAbsent(date, d -> new DayPlan(d, dayTypeOf(d, holidays)));
            Employee emp = employeeMap.get(sa.getEmployeeId());
            SlotAssignment slot = new SlotAssignment(
                    sa.getEmployeeId(),
                    emp != null ? emp.getName() : "Unknown",
                    emp != null ? emp.getRole() : null,
                    date,
                    LocalTime.parse(sa.getStartTime()),
                    LocalTime.parse(sa.getEndTime()),
                    sa.getBreakStart() != null ? LocalTime.parse(sa.getBreakStart()) : null,
                    sa.getBreakEnd() != null ? LocalTime.parse(sa.getBreakEnd()) : null);
            plan.addAssignment(slot);
        }

        List<DayPlan> days = new ArrayList<>(plansByDate.values());

        WeekAccumulator accumulator = new WeekAccumulator();
        for (DayPlan day : days) {
            for (SlotAssignment slot : day.getAssignments()) {
                accumulator.addHours(slot.getEmployeeId(), slot.hoursWorked());
            }
        }

        List<ValidationMessage> messages = new ScheduleValidator().validate(days, accumulator, idToName);
        List<ValidationMessage> errors = messages.stream()
                .filter(m -> m.getSeverity() == Severity.ERROR)
                .toList();
        if (!errors.isEmpty()) {
            throw new ScheduleHasValidationErrorsException(errors);
        }

        week.setStatus(WeekStatus.PUBLISHED);
        week.setPublishedAt(LocalDateTime.now());
        ScheduleWeek saved = scheduleWeekRepository.save(week);
        return buildWeekResponse(saved);
    }

    private WeekResponse buildWeekResponse(ScheduleWeek week) {
        int isoYear = week.getIsoYear();
        int isoWeek = week.getIsoWeek();

        LocalDate weekStart = LocalDate.of(isoYear, 1, 4)
                .with(WeekFields.ISO.weekOfWeekBasedYear(), isoWeek)
                .with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);

        Set<LocalDate> holidays = holidayRepository.findBetween(weekStart, weekEnd)
                .stream()
                .map(PublicHoliday::getDate)
                .collect(Collectors.toSet());

        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByScheduleWeekId(week.getId());

        Map<Long, Employee> employeeMap = employeeRepository.findAll()
                .stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        Map<Long, String> idToName = employeeMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getName()));

        // Rebuild DayPlan + SlotAssignment graph for validation
        Map<LocalDate, DayPlan> plansByDate = new TreeMap<>();
        for (ShiftAssignment sa : assignments) {
            LocalDate date = sa.getDate();
            DayPlan plan = plansByDate.computeIfAbsent(date, d -> new DayPlan(d, dayTypeOf(d, holidays)));
            Employee emp = employeeMap.get(sa.getEmployeeId());
            SlotAssignment slot = new SlotAssignment(
                    sa.getEmployeeId(),
                    emp != null ? emp.getName() : "Unknown",
                    emp != null ? emp.getRole() : null,
                    date,
                    LocalTime.parse(sa.getStartTime()),
                    LocalTime.parse(sa.getEndTime()),
                    sa.getBreakStart() != null ? LocalTime.parse(sa.getBreakStart()) : null,
                    sa.getBreakEnd() != null ? LocalTime.parse(sa.getBreakEnd()) : null);
            plan.addAssignment(slot);
        }

        List<DayPlan> days = new ArrayList<>(plansByDate.values());

        WeekAccumulator accumulator = new WeekAccumulator();
        for (DayPlan day : days) {
            for (SlotAssignment slot : day.getAssignments()) {
                accumulator.addHours(slot.getEmployeeId(), slot.hoursWorked());
            }
        }

        List<ValidationMessage> validationMessages = new ScheduleValidator().validate(days, accumulator, idToName);

        // Build DayResponse from ShiftAssignment entities to preserve assignment id
        Map<LocalDate, List<ShiftAssignment>> byDate = assignments.stream()
                .collect(Collectors.groupingBy(ShiftAssignment::getDate, TreeMap::new, Collectors.toList()));

        List<DayResponse> dayResponses = byDate.entrySet().stream()
                .map(entry -> {
                    LocalDate date = entry.getKey();
                    DayType dayType = dayTypeOf(date, holidays);
                    List<AssignmentResponse> assignmentResponses = entry.getValue().stream()
                            .map(sa -> {
                                Employee emp = employeeMap.get(sa.getEmployeeId());
                                EmployeeDto employeeDto = emp != null
                                        ? new EmployeeDto(emp.getId(), emp.getName(), emp.getRole().name())
                                        : new EmployeeDto(sa.getEmployeeId(), "Unknown", null);
                                double hours = computeHours(
                                        sa.getStartTime(), sa.getEndTime(),
                                        sa.getBreakStart(), sa.getBreakEnd());
                                return new AssignmentResponse(sa.getId(), employeeDto,
                                        sa.getStartTime(), sa.getEndTime(),
                                        sa.getBreakStart(), sa.getBreakEnd(), hours);
                            })
                            .collect(Collectors.toList());
                    return new DayResponse(date, date.getDayOfWeek().name(), dayType.name(), assignmentResponses);
                })
                .collect(Collectors.toList());

        List<EmployeeSummaryResponse> employeeSummaries = employeeMap.values().stream()
                .map(emp -> {
                    double hours = accumulator.getWeeklyHours(emp.getId());
                    EmployeeDto employeeDto = new EmployeeDto(emp.getId(), emp.getName(), emp.getRole().name());
                    String summaryStatus;
                    if (hours > ShiftTemplates.OVERTIME_THRESHOLD_HOURS) summaryStatus = "OVERTIME";
                    else if (hours < ShiftTemplates.UNDERTIME_THRESHOLD_HOURS) summaryStatus = "UNDERTIME";
                    else summaryStatus = "OK";
                    return new EmployeeSummaryResponse(employeeDto, hours, summaryStatus);
                })
                .collect(Collectors.toList());

        List<ValidationMessageResponse> validationMessageResponses = validationMessages.stream()
                .map(vm -> new ValidationMessageResponse(
                        vm.getSeverity().name(), vm.getDate(), vm.getHour(), vm.getMessage()))
                .collect(Collectors.toList());

        return new WeekResponse(isoYear, isoWeek, week.getStatus().name(),
                dayResponses, employeeSummaries, validationMessageResponses);
    }

    private static double computeHours(String startTime, String endTime, String breakStart, String breakEnd) {
        long minutes = Duration.between(LocalTime.parse(startTime), LocalTime.parse(endTime)).toMinutes();
        if (breakStart != null && breakEnd != null) {
            minutes -= Duration.between(LocalTime.parse(breakStart), LocalTime.parse(breakEnd)).toMinutes();
        }
        return minutes / 60.0;
    }

    private static DayType dayTypeOf(LocalDate date, Set<LocalDate> holidays) {
        if (holidays.contains(date)) return DayType.HOLIDAY;
        return switch (date.getDayOfWeek()) {
            case SATURDAY -> DayType.SATURDAY;
            case SUNDAY -> DayType.SUNDAY;
            default -> DayType.WEEKDAY;
        };
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

    private static ShiftAssignment toShiftAssignment(AssignmentWriteRequest req, ScheduleWeek week) {
        ShiftAssignment sa = new ShiftAssignment();
        sa.setScheduleWeekId(week.getId());
        sa.setEmployeeId(req.getEmployeeId());
        sa.setDate(req.getDate());
        sa.setStartTime(req.getStartTime());
        sa.setEndTime(req.getEndTime());
        sa.setBreakStart(req.getBreakStart());
        sa.setBreakEnd(req.getBreakEnd());
        return sa;
    }
}
