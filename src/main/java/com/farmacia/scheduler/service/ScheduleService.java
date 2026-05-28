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
import java.time.temporal.ChronoUnit;
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

    private static LocalDate isoWeekMonday(int isoYear, int isoWeek) {
        // Jan 4 is always in ISO week 1, anchors correctly across year boundaries
        return LocalDate.of(isoYear, 1, 4)
                .with(WeekFields.ISO.weekOfWeekBasedYear(), isoWeek)
                .with(DayOfWeek.MONDAY);
    }

    @Transactional
    public WeekResponse generate(int isoYear, int isoWeek) {
        if (scheduleWeekRepository.findByIsoYearAndIsoWeek(isoYear, isoWeek).isPresent()) {
            throw new ScheduleAlreadyExistsException(
                    "Schedule already exists for ISO year %d week %d".formatted(isoYear, isoWeek));
        }

        LocalDate weekStart = isoWeekMonday(isoYear, isoWeek);
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

        List<ShiftAssignment> assignments = request.assignments().stream()
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

        LocalDate weekStart = isoWeekMonday(isoYear, isoWeek);
        LocalDate weekEnd = weekStart.plusDays(6);

        Set<LocalDate> holidays = holidayRepository.findBetween(weekStart, weekEnd)
                .stream()
                .map(PublicHoliday::getDate)
                .collect(Collectors.toSet());

        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByScheduleWeekId(week.getId());
        List<EmployeeAbsence> absences = absenceRepository.findOverlapping(weekStart, weekEnd);

        Map<Long, Employee> employeeMap = employeeRepository.findAll()
                .stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        Map<Long, String> idToName = employeeMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getName()));

        Map<Long, Double> absenceCredits = computeAbsenceCredits(absences, weekStart, weekEnd);

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

        List<ValidationMessage> messages = new ScheduleValidator().validate(days, accumulator, idToName, absenceCredits);
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

    @Transactional
    public WeekResponse regenerate(int isoYear, int isoWeek) {
        ScheduleWeek week = scheduleWeekRepository.findByIsoYearAndIsoWeek(isoYear, isoWeek)
                .orElseThrow(() -> new ScheduleNotFoundException(
                        "No schedule found for ISO year %d week %d".formatted(isoYear, isoWeek)));

        if (week.getStatus() == WeekStatus.PUBLISHED) {
            throw new ScheduleAlreadyPublishedException(
                    "Cannot regenerate a published schedule for ISO year %d week %d".formatted(isoYear, isoWeek));
        }

        LocalDate monday = isoWeekMonday(isoYear, isoWeek);
        LocalDate weekEnd = monday.plusDays(6);

        holidayGeneratorService.ensureGenerated(monday.getYear());
        if (weekEnd.getYear() != monday.getYear()) {
            holidayGeneratorService.ensureGenerated(weekEnd.getYear());
        }

        shiftAssignmentRepository.deleteByScheduleWeekId(week.getId());

        List<Employee> employees = employeeRepository.findAll();
        List<EmployeeAbsence> absences = absenceRepository.findOverlapping(monday, weekEnd);
        Set<LocalDate> holidays = holidayRepository.findBetween(monday, weekEnd)
                .stream().map(PublicHoliday::getDate).collect(Collectors.toSet());
        List<ShiftAssignment> priorAssignments =
                shiftAssignmentRepository.findByDateRange(monday.minusWeeks(4), monday.minusDays(1));

        Map<Long, LocalDate> effectiveLastWeekendWorked = priorAssignments.stream()
                .filter(a -> a.getDate().getDayOfWeek() == DayOfWeek.SATURDAY)
                .collect(Collectors.toMap(
                        ShiftAssignment::getEmployeeId,
                        ShiftAssignment::getDate,
                        (a, b) -> !a.isBefore(b) ? a : b));

        WeekResult result = scheduleEngine.generate(
                isoYear, isoWeek, monday, employees, absences, holidays, priorAssignments,
                effectiveLastWeekendWorked);

        List<ShiftAssignment> assignments = new ArrayList<>();
        for (DayPlan day : result.getDays()) {
            for (SlotAssignment slot : day.getAssignments()) {
                assignments.add(getShiftAssignment(slot, week));
            }
        }
        shiftAssignmentRepository.saveAll(assignments);

        week.setGeneratedAt(LocalDateTime.now());
        scheduleWeekRepository.save(week);
        return buildWeekResponse(week);
    }

    @Transactional
    public WeekResponse replan(int isoYear, int isoWeek) {
        ScheduleWeek week = scheduleWeekRepository.findByIsoYearAndIsoWeek(isoYear, isoWeek)
                .orElseThrow(() -> new ScheduleNotFoundException(
                        "No schedule found for ISO year %d week %d".formatted(isoYear, isoWeek)));

        LocalDate monday = isoWeekMonday(isoYear, isoWeek);
        LocalDate weekEnd = monday.plusDays(6);
        LocalDate today = LocalDate.now();

        // Nothing to regenerate if today is after the week
        if (today.isAfter(weekEnd)) {
            return buildWeekResponse(week);
        }

        LocalDate replanFrom = today.isBefore(monday) ? monday : today;

        holidayGeneratorService.ensureGenerated(monday.getYear());
        if (weekEnd.getYear() != monday.getYear()) {
            holidayGeneratorService.ensureGenerated(weekEnd.getYear());
        }

        shiftAssignmentRepository.deleteByScheduleWeekIdAndDateFrom(week.getId(), replanFrom);

        List<ShiftAssignment> lockedAssignments =
                shiftAssignmentRepository.findByScheduleWeekIdAndDateBefore(week.getId(), replanFrom);

        List<Employee> employees = employeeRepository.findAll();
        List<EmployeeAbsence> absences = absenceRepository.findOverlapping(monday, weekEnd);
        Set<LocalDate> holidays = holidayRepository.findBetween(monday, weekEnd)
                .stream().map(PublicHoliday::getDate).collect(Collectors.toSet());
        List<ShiftAssignment> priorAssignments =
                shiftAssignmentRepository.findByDateRange(monday.minusWeeks(4), monday.minusDays(1));

        Map<Long, LocalDate> effectiveLastWeekendWorked = new HashMap<>(priorAssignments.stream()
                .filter(a -> a.getDate().getDayOfWeek() == DayOfWeek.SATURDAY)
                .collect(Collectors.toMap(
                        ShiftAssignment::getEmployeeId,
                        ShiftAssignment::getDate,
                        (a, b) -> !a.isBefore(b) ? a : b)));

        WeekResult result = scheduleEngine.replan(
                isoYear, isoWeek, monday, replanFrom,
                employees, absences, holidays, priorAssignments,
                effectiveLastWeekendWorked, lockedAssignments);

        // Persist only newly generated assignments (locked ones are already in the DB)
        List<ShiftAssignment> newAssignments = new ArrayList<>();
        for (DayPlan day : result.getDays()) {
            if (day.getDate().isBefore(replanFrom)) continue;
            for (SlotAssignment slot : day.getAssignments()) {
                newAssignments.add(getShiftAssignment(slot, week));
            }
        }
        shiftAssignmentRepository.saveAll(newAssignments);

        week.setStatus(WeekStatus.DRAFT);
        week.setLastEditedAt(LocalDateTime.now());
        scheduleWeekRepository.save(week);
        return buildWeekResponse(week);
    }

    @Transactional
    public void delete(int isoYear, int isoWeek) {
        ScheduleWeek week = scheduleWeekRepository.findByIsoYearAndIsoWeek(isoYear, isoWeek)
                .orElseThrow(() -> new ScheduleNotFoundException(
                        "No schedule found for ISO year %d week %d".formatted(isoYear, isoWeek)));
        if (week.getStatus() == WeekStatus.PUBLISHED) {
            throw new ScheduleAlreadyPublishedException(
                    "Cannot delete a published schedule for ISO year %d week %d".formatted(isoYear, isoWeek));
        }
        shiftAssignmentRepository.deleteByScheduleWeekId(week.getId());
        scheduleWeekRepository.delete(week);
    }

    private WeekResponse buildWeekResponse(ScheduleWeek week) {
        int isoYear = week.getIsoYear();
        int isoWeek = week.getIsoWeek();

        LocalDate weekStart = isoWeekMonday(isoYear, isoWeek);
        LocalDate weekEnd = weekStart.plusDays(6);

        Set<LocalDate> holidays = holidayRepository.findBetween(weekStart, weekEnd)
                .stream()
                .map(PublicHoliday::getDate)
                .collect(Collectors.toSet());

        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByScheduleWeekId(week.getId());
        List<EmployeeAbsence> absences = absenceRepository.findOverlapping(weekStart, weekEnd);

        Map<Long, Employee> employeeMap = employeeRepository.findAll()
                .stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        Map<Long, String> idToName = employeeMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getName()));

        Map<Long, Double> absenceCredits = computeAbsenceCredits(absences, weekStart, weekEnd);

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

        List<ValidationMessage> validationMessages = new ScheduleValidator().validate(days, accumulator, idToName, absenceCredits);

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
                    double worked = accumulator.getWeeklyHours(emp.getId());
                    double effective = worked + absenceCredits.getOrDefault(emp.getId(), 0.0);
                    EmployeeDto employeeDto = new EmployeeDto(emp.getId(), emp.getName(), emp.getRole().name());
                    String summaryStatus;
                    if (worked > ShiftTemplates.OVERTIME_THRESHOLD_HOURS) summaryStatus = "OVERTIME";
                    else if (effective < ShiftTemplates.UNDERTIME_THRESHOLD_HOURS) summaryStatus = "UNDERTIME";
                    else summaryStatus = "OK";
                    return new EmployeeSummaryResponse(employeeDto, worked, effective, summaryStatus);
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

    //Used only for UNDERTIME threshold checks - not for scheduling decisions
    private static Map<Long, Double> computeAbsenceCredits(
            List<EmployeeAbsence> absences, LocalDate weekStart, LocalDate weekEnd) {
        Map<Long, Double> credits = new HashMap<>();
        for (EmployeeAbsence a : absences) {
            if (a.getType() == AbsenceType.FOLGA) continue;
            LocalDate from = a.getStartDate().isBefore(weekStart) ? weekStart : a.getStartDate();
            LocalDate to = a.getEndDate().isAfter(weekEnd) ? weekEnd : a.getEndDate();
            if (!from.isAfter(to)) {
                long days = ChronoUnit.DAYS.between(from, to) + 1;
                credits.merge(a.getEmployeeId(), days * 8.0, Double::sum);
            }
        }
        return credits;
    }

    private static ShiftAssignment toShiftAssignment(AssignmentWriteRequest req, ScheduleWeek week) {
        ShiftAssignment sa = new ShiftAssignment();
        sa.setScheduleWeekId(week.getId());
        sa.setEmployeeId(req.employeeId());
        sa.setDate(req.date());
        sa.setStartTime(req.startTime());
        sa.setEndTime(req.endTime());
        sa.setBreakStart(req.breakStart());
        sa.setBreakEnd(req.breakEnd());
        return sa;
    }
}
