package com.farmacia.scheduler.engine;

import com.farmacia.scheduler.engine.model.*;
import com.farmacia.scheduler.model.Employee;
import com.farmacia.scheduler.model.EmployeeAbsence;
import com.farmacia.scheduler.model.ShiftAssignment;

import com.farmacia.scheduler.model.AbsenceType;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ScheduleEngine {

    private final WeekendAssigner weekendAssigner = new WeekendAssigner();
    private final WeekdayFiller weekdayFiller = new WeekdayFiller();
    private final ShiftTrimmer trimmer = new ShiftTrimmer();
    private final ScheduleValidator validator = new ScheduleValidator();

    public WeekResult generate(
            int isoYear,
            int isoWeek,
            LocalDate monday,
            List<Employee> employees,
            List<EmployeeAbsence> absences,
            Set<LocalDate> holidays,
            List<ShiftAssignment> priorAssignments,
            Map<Long, LocalDate> effectiveLastWeekendWorked) {

        WeekAccumulator accumulator = new WeekAccumulator();
        seedPriorWeeks(accumulator, priorAssignments);

        List<ValidationMessage> messages = new ArrayList<>();
        List<DayPlan> days = new ArrayList<>();

        LocalDate saturday = monday.plusDays(5);
        LocalDate sunday = monday.plusDays(6);

        // Phase 1a: Weekend
        Set<Long> absentSat = absentEmployeesOn(absences, saturday);
        Set<Long> absentSun = absentEmployeesOn(absences, sunday);
        List<DayPlan> weekendDays = weekendAssigner.assignWeekend(
                saturday, sunday, employees, holidays, absentSat, absentSun, accumulator, messages,
                effectiveLastWeekendWorked);

        // Merge Phase 1a weekend workers into the rotation map for Phase 1b sort.
        // Employees who worked this weekend's Saturday take precedence over prior-history dates.
        Map<Long, LocalDate> phase1bRotation = new HashMap<>(effectiveLastWeekendWorked);
        weekendDays.stream()
                .filter(d -> d.getDate().equals(saturday))
                .findFirst()
                .ifPresent(satPlan -> satPlan.getAssignments()
                        .forEach(slot -> phase1bRotation.put(slot.getEmployeeId(), saturday)));

        // Phase 1b: Mid-week holidays (Mon-Fri only)
        Map<LocalDate, DayPlan> holidayPlans = new HashMap<>();
        for (LocalDate date = monday; !date.isAfter(monday.plusDays(4)); date = date.plusDays(1)) {
            if (holidays.contains(date)) {
                Set<Long> absent = absentEmployeesOn(absences, date);
                DayPlan plan = weekendAssigner.assignHoliday(
                        date, employees, absent, accumulator, messages, phase1bRotation);
                holidayPlans.put(date, plan);
            }
        }

        // Phase 2: Weekday fill (skip mid-week holidays — already assigned)
        for (LocalDate date = monday; !date.isAfter(monday.plusDays(5)); date = date.plusDays(1)) {
            if (date.equals(saturday)) continue; // handled by weekend
            if (holidayPlans.containsKey(date)) {
                days.add(holidayPlans.get(date));
                continue;
            }

            Set<Long> absent = absentEmployeesOn(absences, date);
            DayType dayType = date.getDayOfWeek() == DayOfWeek.SATURDAY ? DayType.SATURDAY : DayType.WEEKDAY;
            DayPlan plan = weekdayFiller.fillWeekday(date, dayType, employees, absent, accumulator, messages);
            days.add(plan);
        }

        days.addAll(weekendDays);
        days.sort(Comparator.comparing(DayPlan::getDate));

        Map<Long, String> idToName = employees.stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName));

        // Phase 3: Trim overtime
        trimmer.trim(days, accumulator, messages, idToName);

        // Phase 4: Validate
        LocalDate weekEnd = monday.plusDays(6);
        Map<Long, Double> absenceCredits = computeAbsenceCredits(absences, monday, weekEnd);
        List<ValidationMessage> validationMessages = validator.validate(days, accumulator, idToName, absenceCredits);
        messages.addAll(validationMessages);

        return new WeekResult(isoYear, isoWeek, days, messages, accumulator.getAllWeeklyHours());
    }

    public WeekResult replan(
            int isoYear, int isoWeek, LocalDate monday, LocalDate today,
            List<Employee> employees, List<EmployeeAbsence> absences,
            Set<LocalDate> holidays, List<ShiftAssignment> priorAssignments,
            Map<Long, LocalDate> effectiveLastWeekendWorked,
            List<ShiftAssignment> lockedAssignments) {

        LocalDate saturday = monday.plusDays(5);
        LocalDate sunday = monday.plusDays(6);

        WeekAccumulator accumulator = new WeekAccumulator();
        seedPriorWeeks(accumulator, priorAssignments);
        for (ShiftAssignment sa : lockedAssignments) {
            accumulator.addHours(sa.getEmployeeId(), computeHours(sa));
        }

        List<ValidationMessage> messages = new ArrayList<>();

        // Reconstruct DayPlans for locked (past) assignments
        Map<Long, Employee> employeeMap = employees.stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        Map<LocalDate, DayPlan> daysByDate = new TreeMap<>();
        for (ShiftAssignment sa : lockedAssignments) {
            LocalDate date = sa.getDate();
            DayPlan plan = daysByDate.computeIfAbsent(date, d -> new DayPlan(d, dayTypeOf(d, holidays)));
            Employee emp = employeeMap.get(sa.getEmployeeId());
            plan.addAssignment(new SlotAssignment(
                    sa.getEmployeeId(),
                    emp != null ? emp.getName() : "Unknown",
                    emp != null ? emp.getRole() : null,
                    date,
                    LocalTime.parse(sa.getStartTime()),
                    LocalTime.parse(sa.getEndTime()),
                    sa.getBreakStart() != null ? LocalTime.parse(sa.getBreakStart()) : null,
                    sa.getBreakEnd() != null ? LocalTime.parse(sa.getBreakEnd()) : null));
        }

        Map<Long, LocalDate> phase1bRotation = new HashMap<>(effectiveLastWeekendWorked);

        // Phase 1a: weekend — only if Saturday is still in the future
        if (!today.isAfter(saturday)) {
            Set<Long> absentSat = absentEmployeesOn(absences, saturday);
            Set<Long> absentSun = absentEmployeesOn(absences, sunday);
            List<DayPlan> weekendDays = weekendAssigner.assignWeekend(
                    saturday, sunday, employees, holidays, absentSat, absentSun, accumulator, messages,
                    effectiveLastWeekendWorked);
            weekendDays.forEach(d -> daysByDate.put(d.getDate(), d));
            weekendDays.stream()
                    .filter(d -> d.getDate().equals(saturday))
                    .findFirst()
                    .ifPresent(satPlan -> satPlan.getAssignments()
                            .forEach(slot -> phase1bRotation.put(slot.getEmployeeId(), saturday)));
        } else if (today.equals(sunday)) {
            // Saturday locked — assign Sunday as standalone using the holiday template
            lockedAssignments.stream()
                    .filter(sa -> sa.getDate().equals(saturday))
                    .forEach(sa -> phase1bRotation.put(sa.getEmployeeId(), saturday));
            Set<Long> absentSun = absentEmployeesOn(absences, sunday);
            DayPlan sunPlan = weekendAssigner.assignHoliday(
                    sunday, employees, absentSun, accumulator, messages, phase1bRotation);
            daysByDate.put(sunday, sunPlan);
        }

        // Phase 1b: mid-week holidays from today (Mon–Fri only)
        LocalDate fillFrom = today.isBefore(monday) ? monday : today;
        for (LocalDate date = fillFrom; !date.isAfter(monday.plusDays(4)); date = date.plusDays(1)) {
            if (!daysByDate.containsKey(date) && holidays.contains(date)) {
                Set<Long> absent = absentEmployeesOn(absences, date);
                DayPlan plan = weekendAssigner.assignHoliday(
                        date, employees, absent, accumulator, messages, phase1bRotation);
                daysByDate.put(date, plan);
            }
        }

        // Phase 2: normal weekdays from today (Mon–Sat, not already assigned, not holidays)
        for (LocalDate date = fillFrom; !date.isAfter(saturday); date = date.plusDays(1)) {
            if (daysByDate.containsKey(date)) continue;
            if (holidays.contains(date)) continue;
            DayType dayType = date.getDayOfWeek() == DayOfWeek.SATURDAY ? DayType.SATURDAY : DayType.WEEKDAY;
            Set<Long> absent = absentEmployeesOn(absences, date);
            DayPlan plan = weekdayFiller.fillWeekday(date, dayType, employees, absent, accumulator, messages);
            daysByDate.put(date, plan);
        }

        List<DayPlan> allDays = new ArrayList<>(daysByDate.values());
        // Trim only touches newly generated days — locked past assignments can't be changed
        List<DayPlan> newDays = allDays.stream()
                .filter(d -> !d.getDate().isBefore(fillFrom))
                .toList();

        Map<Long, String> idToName = employees.stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName));

        // Phase 3: trim new days only
        trimmer.trim(newDays, accumulator, messages, idToName);

        // Phase 4: validate all days (locked + new)
        Map<Long, Double> absenceCredits = computeAbsenceCredits(absences, monday, sunday);
        messages.addAll(validator.validate(allDays, accumulator, idToName, absenceCredits));

        return new WeekResult(isoYear, isoWeek, allDays, messages, accumulator.getAllWeeklyHours());
    }

    private static DayType dayTypeOf(LocalDate date, Set<LocalDate> holidays) {
        if (holidays.contains(date)) return DayType.HOLIDAY;
        return switch (date.getDayOfWeek()) {
            case SATURDAY -> DayType.SATURDAY;
            case SUNDAY -> DayType.SUNDAY;
            default -> DayType.WEEKDAY;
        };
    }

    private void seedPriorWeeks(WeekAccumulator accumulator, List<ShiftAssignment> priorAssignments) {
        Map<Long, Double> hoursByEmployee = new HashMap<>();
        for (ShiftAssignment assignment : priorAssignments) {
            double hours = computeHours(assignment);
            hoursByEmployee.merge(assignment.getEmployeeId(), hours, Double::sum);
        }
        for (Map.Entry<Long, Double> entry : hoursByEmployee.entrySet()) {
            accumulator.seedPriorWeeksHours(entry.getKey(), entry.getValue());
        }
    }

    private double computeHours(ShiftAssignment assignment) {
        java.time.LocalTime start = java.time.LocalTime.parse(assignment.getStartTime());
        java.time.LocalTime end = java.time.LocalTime.parse(assignment.getEndTime());
        long minutes = java.time.Duration.between(start, end).toMinutes();
        if (assignment.getBreakStart() != null && assignment.getBreakEnd() != null) {
            java.time.LocalTime bStart = java.time.LocalTime.parse(assignment.getBreakStart());
            java.time.LocalTime bEnd = java.time.LocalTime.parse(assignment.getBreakEnd());
            minutes -= java.time.Duration.between(bStart, bEnd).toMinutes();
        }
        return minutes / 60.0;
    }

    // Credits 8h per absence day (non-FOLGA). Used only for UNDERTIME check, not scheduling.
    private Map<Long, Double> computeAbsenceCredits(
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

    private Set<Long> absentEmployeesOn(List<EmployeeAbsence> absences, LocalDate date) {
        return absences.stream()
                .filter(a -> !date.isBefore(a.getStartDate()) && !date.isAfter(a.getEndDate()))
                .map(EmployeeAbsence::getEmployeeId)
                .collect(Collectors.toSet());
    }
}