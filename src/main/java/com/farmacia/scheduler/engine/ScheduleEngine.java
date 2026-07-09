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
import java.time.temporal.WeekFields;
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

        Set<Long> hadBreakShiftLastWeek = computeHadBreakShiftLastWeek(priorAssignments, monday);
        Map<Long, Boolean> lastWeekendPattern = computeLastWeekendPattern(priorAssignments);
        Map<Integer, Long> slotOwnerForWeek = new HashMap<>();
        Map<Long, Integer> breaksThisWeek = new HashMap<>();

        List<ValidationMessage> messages = new ArrayList<>();
        List<DayPlan> days = new ArrayList<>();

        LocalDate saturday = monday.plusDays(5);
        LocalDate sunday = monday.plusDays(6);

        // Phase 1a: Weekend
        Set<Long> absentSat = absentEmployeesOn(absences, saturday);
        Set<Long> absentSun = absentEmployeesOn(absences, sunday);
        List<DayPlan> weekendDays = weekendAssigner.assignWeekend(
                saturday, sunday, employees, holidays, absentSat, absentSun, accumulator, messages,
                effectiveLastWeekendWorked, lastWeekendPattern);

        Map<Long, LocalDate> phase1bRotation = new HashMap<>(effectiveLastWeekendWorked);
        weekendDays.stream()
                .filter(d -> d.getDate().equals(saturday))
                .findFirst()
                .ifPresent(satPlan -> satPlan.getAssignments()
                        .forEach(slot -> phase1bRotation.put(slot.getEmployeeId(), saturday)));

        // Phase 1c: Programmatic weekday folgas for everyone who worked the weekend.
        // No contract tiers — every weekend worker gets 2 compensating weekday folgas,
        // landing them at ~37h. See decisions/016.
        Set<Long> weekendWorkerIds = weekendDays.stream()
                .flatMap(d -> d.getAssignments().stream())
                .map(SlotAssignment::getEmployeeId)
                .collect(Collectors.toSet());
        List<Employee> folgaWorkers = employees.stream()
                .filter(emp -> weekendWorkerIds.contains(emp.getId()))
                .sorted(Comparator.comparingLong(Employee::getId))
                .toList();
        Map<LocalDate, Set<Long>> programmaticFolgas = computeFolgaDays(folgaWorkers, monday);

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
            if (date.equals(saturday)) continue;
            if (holidayPlans.containsKey(date)) {
                days.add(holidayPlans.get(date));
                continue;
            }

            Set<Long> absent = new HashSet<>(absentEmployeesOn(absences, date));
            absent.addAll(programmaticFolgas.getOrDefault(date, Set.of()));
            DayType dayType = date.getDayOfWeek() == DayOfWeek.SATURDAY ? DayType.SATURDAY : DayType.WEEKDAY;
            DayPlan plan = weekdayFiller.fillWeekday(
                    date, dayType, employees, absent, accumulator,
                    slotOwnerForWeek, hadBreakShiftLastWeek, weekendWorkerIds, breaksThisWeek, messages);
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

        Set<Long> hadBreakShiftLastWeek = computeHadBreakShiftLastWeek(priorAssignments, monday);
        Map<Long, Boolean> lastWeekendPattern = computeLastWeekendPattern(priorAssignments);
        // Seed slot owners from locked weekday assignments (past days of this week)
        Map<Integer, Long> slotOwnerForWeek = seedSlotOwners(lockedAssignments);
        Map<Long, Integer> breaksThisWeek = new HashMap<>();

        List<ValidationMessage> messages = new ArrayList<>();

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
                    effectiveLastWeekendWorked, lastWeekendPattern);
            weekendDays.forEach(d -> daysByDate.put(d.getDate(), d));
            weekendDays.stream()
                    .filter(d -> d.getDate().equals(saturday))
                    .findFirst()
                    .ifPresent(satPlan -> satPlan.getAssignments()
                            .forEach(slot -> phase1bRotation.put(slot.getEmployeeId(), saturday)));
        } else if (today.equals(sunday)) {
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
            // Replan does not recompute weekend rotation / programmatic folgas (pre-existing
            // limitation), so it has no weekend-worker set to anchor on — pass empty.
            DayPlan plan = weekdayFiller.fillWeekday(
                    date, dayType, employees, absent, accumulator,
                    slotOwnerForWeek, hadBreakShiftLastWeek, Set.of(), breaksThisWeek, messages);
            daysByDate.put(date, plan);
        }

        List<DayPlan> allDays = new ArrayList<>(daysByDate.values());
        List<DayPlan> newDays = allDays.stream()
                .filter(d -> !d.getDate().isBefore(fillFrom))
                .toList();

        Map<Long, String> idToName = employees.stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName));

        trimmer.trim(newDays, accumulator, messages, idToName);

        Map<Long, Double> absenceCredits = computeAbsenceCredits(absences, monday, sunday);
        messages.addAll(validator.validate(allDays, accumulator, idToName, absenceCredits));

        return new WeekResult(isoYear, isoWeek, allDays, messages, accumulator.getAllWeeklyHours());
    }

    /**
     * Computes which employees had a break shift in the ISO week immediately preceding {@code monday}.
     */
    /** Last Saturday each employee worked: was it the morning (Pair A) side? Drives A<->B alternation. */
    private Map<Long, Boolean> computeLastWeekendPattern(List<ShiftAssignment> prior) {
        Map<Long, ShiftAssignment> lastSat = new HashMap<>();
        for (ShiftAssignment a : prior) {
            if (a.getDate().getDayOfWeek() != DayOfWeek.SATURDAY) continue;
            ShiftAssignment cur = lastSat.get(a.getEmployeeId());
            if (cur == null || a.getDate().isAfter(cur.getDate())) {
                lastSat.put(a.getEmployeeId(), a);
            }
        }
        Map<Long, Boolean> wasPairA = new HashMap<>();
        lastSat.forEach((id, a) ->
                wasPairA.put(id, LocalTime.parse(a.getStartTime()).getHour() < 12));
        return wasPairA;
    }

    private Set<Long> computeHadBreakShiftLastWeek(List<ShiftAssignment> priorAssignments, LocalDate monday) {
        LocalDate lastMonday = monday.minusWeeks(1);
        LocalDate lastSunday = monday.minusDays(1);
        return priorAssignments.stream()
                .filter(sa -> !sa.getDate().isBefore(lastMonday) && !sa.getDate().isAfter(lastSunday))
                .filter(sa -> sa.getBreakStart() != null)
                .map(ShiftAssignment::getEmployeeId)
                .collect(Collectors.toSet());
    }

    /**
     * Seeds the slot-owner map from locked weekday assignments (used during replan).
     * Matches each assignment to a WEEKDAY_SLOTS entry by (start, end, breakStart).
     * For slots with identical templates (e.g. both 08:00–16:00), the first unoccupied
     * matching slot index is used.
     */
    private Map<Integer, Long> seedSlotOwners(List<ShiftAssignment> lockedAssignments) {
        Map<Integer, Long> owners = new HashMap<>();
        LocalTime[][] slots = ShiftTemplates.WEEKDAY_SLOTS;

        for (ShiftAssignment sa : lockedAssignments) {
            DayOfWeek dow = sa.getDate().getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) continue;
            if (owners.containsValue(sa.getEmployeeId())) continue; // employee already owns a slot

            LocalTime start    = LocalTime.parse(sa.getStartTime());
            LocalTime end      = LocalTime.parse(sa.getEndTime());
            LocalTime brkStart = sa.getBreakStart() != null ? LocalTime.parse(sa.getBreakStart()) : null;

            for (int i = 0; i < slots.length; i++) {
                if (owners.containsKey(i)) continue; // slot already claimed
                LocalTime[] s = slots[i];
                if (s[0].equals(start) && s[1].equals(end) && Objects.equals(s[2], brkStart)) {
                    owners.put(i, sa.getEmployeeId());
                    break;
                }
            }
        }
        return owners;
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
        if (priorAssignments.isEmpty()) return;

        WeekFields iso = WeekFields.ISO;
        long distinctWeeks = priorAssignments.stream()
                .map(a -> a.getDate().get(iso.weekOfWeekBasedYear()) * 10000
                        + a.getDate().get(iso.weekBasedYear()))
                .distinct()
                .count();

        Map<Long, Double> hoursByEmployee = new HashMap<>();
        for (ShiftAssignment assignment : priorAssignments) {
            double hours = computeHours(assignment);
            hoursByEmployee.merge(assignment.getEmployeeId(), hours, Double::sum);
        }

        for (Map.Entry<Long, Double> entry : hoursByEmployee.entrySet()) {
            accumulator.seedPriorWeeksHours(entry.getKey(), entry.getValue() / distinctWeeks);
        }
    }

    private double computeHours(ShiftAssignment assignment) {
        LocalTime start = LocalTime.parse(assignment.getStartTime());
        LocalTime end   = LocalTime.parse(assignment.getEndTime());
        long minutes = java.time.Duration.between(start, end).toMinutes();
        if (assignment.getBreakStart() != null && assignment.getBreakEnd() != null) {
            LocalTime bStart = LocalTime.parse(assignment.getBreakStart());
            LocalTime bEnd   = LocalTime.parse(assignment.getBreakEnd());
            minutes -= java.time.Duration.between(bStart, bEnd).toMinutes();
        }
        return minutes / 60.0;
    }

    private Map<Long, Double> computeAbsenceCredits(
            List<EmployeeAbsence> absences, LocalDate weekStart, LocalDate weekEnd) {
        Map<Long, Double> credits = new HashMap<>();
        for (EmployeeAbsence a : absences) {
            if (a.getType() == AbsenceType.FOLGA) continue;
            LocalDate from = a.getStartDate().isBefore(weekStart) ? weekStart : a.getStartDate();
            LocalDate to   = a.getEndDate().isAfter(weekEnd)     ? weekEnd   : a.getEndDate();
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

    /**
     * Assigns 2 consecutive weekday folgas per worker, spreading evenly across Mon-Fri.
     * No more than 2 workers will be absent on any single day.
     * Workers sorted by ID; first available consecutive pair (Mon-Tue through Thu-Fri) is used.
     */
    private Map<LocalDate, Set<Long>> computeFolgaDays(List<Employee> folgaWorkers, LocalDate monday) {
        LocalDate[] weekdays = new LocalDate[5];
        for (int i = 0; i < 5; i++) weekdays[i] = monday.plusDays(i);
        int[] folgaCount = new int[5];
        Map<LocalDate, Set<Long>> result = new HashMap<>();

        for (Employee worker : folgaWorkers) {
            boolean assigned = false;
            for (int i = 0; i <= 3 && !assigned; i++) {
                if (folgaCount[i] < 2 && folgaCount[i + 1] < 2) {
                    folgaCount[i]++;
                    folgaCount[i + 1]++;
                    result.computeIfAbsent(weekdays[i],     d -> new HashSet<>()).add(worker.getId());
                    result.computeIfAbsent(weekdays[i + 1], d -> new HashSet<>()).add(worker.getId());
                    assigned = true;
                }
            }
            // If no consecutive pair found (>4 workers with folgas), worker gets no folgas this week.
        }
        return result;
    }
}
