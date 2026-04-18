package com.farmacia.scheduler.engine;

import com.farmacia.scheduler.engine.model.*;
import com.farmacia.scheduler.model.Employee;
import com.farmacia.scheduler.model.EmployeeAbsence;
import com.farmacia.scheduler.model.ShiftAssignment;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class ScheduleEngine {

    private final WeekendAssigner weekendAssigner = new WeekendAssigner();
    private final WeekdayFiller weekdayFiller = new WeekdayFiller();
    private final ShiftTrimmer trimmer = new ShiftTrimmer();
    private final ScheduleValidator validator = new ScheduleValidator();

    /**
     * Generate a full week schedule.
     *
     * @param isoYear            ISO week year
     * @param isoWeek            ISO week number
     * @param monday             the Monday of the target week
     * @param employees          all active employees (excluding those on long absences at query time)
     * @param absences           all absences overlapping this week
     * @param holidays           public holidays overlapping this week
     * @param priorAssignments   assignments from the last 4 weeks (for hour lookback)
     * @return WeekResult with days, validation messages, hour totals
     */
    public WeekResult generate(
            int isoYear,
            int isoWeek,
            LocalDate monday,
            List<Employee> employees,
            List<EmployeeAbsence> absences,
            Set<LocalDate> holidays,
            List<ShiftAssignment> priorAssignments) {

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
                saturday, sunday, employees, holidays, absentSat, absentSun, accumulator, messages);

        // Phase 1b: Mid-week holidays (Mon-Fri only)
        Map<LocalDate, DayPlan> holidayPlans = new HashMap<>();
        for (LocalDate date = monday; !date.isAfter(monday.plusDays(4)); date = date.plusDays(1)) {
            if (holidays.contains(date)) {
                Set<Long> absent = absentEmployeesOn(absences, date);
                DayPlan plan = weekendAssigner.assignHoliday(date, employees, absent, accumulator, messages);
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
        List<ValidationMessage> validationMessages = validator.validate(days, accumulator, idToName);
        messages.addAll(validationMessages);

        return new WeekResult(isoYear, isoWeek, days, messages, accumulator.getAllWeeklyHours());
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

    private Set<Long> absentEmployeesOn(List<EmployeeAbsence> absences, LocalDate date) {
        return absences.stream()
                .filter(a -> !date.isBefore(a.getStartDate()) && !date.isAfter(a.getEndDate()))
                .map(EmployeeAbsence::getEmployeeId)
                .collect(Collectors.toSet());
    }
}