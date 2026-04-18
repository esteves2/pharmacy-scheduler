package com.farmacia.scheduler.engine;

import com.farmacia.scheduler.engine.model.*;
import com.farmacia.scheduler.model.Employee;
import com.farmacia.scheduler.model.Role;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public class WeekendAssigner {

    /**
     * Phase 1a: Assign Sat/Sun pairs.
     * Returns DayPlans for Saturday and Sunday, updates the accumulator.
     * Mutates employee.lastWeekendWorked in memory.
     */
    public List<DayPlan> assignWeekend(
            LocalDate saturday,
            LocalDate sunday,
            List<Employee> employees,
            Set<LocalDate> holidays,
            Set<Long> absentEmployeeIdsSat,
            Set<Long> absentEmployeeIdsSun,
            WeekAccumulator accumulator,
            List<ValidationMessage> messages) {

        boolean satIsHoliday = holidays.contains(saturday);

        Set<Long> absentEither = new HashSet<>(absentEmployeeIdsSat);
        absentEither.addAll(absentEmployeeIdsSun);

        List<Employee> available = employees.stream()
                .filter(emp -> !absentEither.contains(emp.getId()))
                .sorted(Comparator
                        .comparing((Employee emp) -> emp.getLastWeekendWorked() == null ? LocalDate.MIN : emp.getLastWeekendWorked())
                        .thenComparing(Employee::getId))
                .collect(Collectors.toList());

        List<Employee> picked = pickWeekendWorkers(available, messages, saturday);

        DayPlan satPlan = new DayPlan(saturday, satIsHoliday ? DayType.HOLIDAY : DayType.SATURDAY);
        DayPlan sunPlan = new DayPlan(sunday, holidays.contains(sunday) ? DayType.HOLIDAY : DayType.SUNDAY);

        if (picked.size() < 4) {
            messages.add(ValidationMessage.warning(saturday, null,
                    "Only " + picked.size() + " workers available for weekend"));
        }

        if (picked.size() >= 2) {
            // Split into two pairs, each with at least 1 F
            List<List<Employee>> pairs = formPairs(picked);
            assignPairToWeekend(pairs.get(0), satPlan, sunPlan, satIsHoliday, true, accumulator);
            if (pairs.size() > 1) {
                assignPairToWeekend(pairs.get(1), satPlan, sunPlan, satIsHoliday, false, accumulator);
            }
        }

        for (Employee emp : picked) {
            emp.setLastWeekendWorked(saturday);
        }

        return List.of(satPlan, sunPlan);
    }

    /**
     * Phase 1b: Assign a mid-week holiday (Mon-Fri).
     * Same template as Sunday: 8-14 / 14-20, 4 workers in 2 pairs.
     */
    public DayPlan assignHoliday(
            LocalDate date,
            List<Employee> employees,
            Set<Long> absentEmployeeIds,
            WeekAccumulator accumulator,
            List<ValidationMessage> messages) {

        List<Employee> available = employees.stream()
                .filter(emp -> !absentEmployeeIds.contains(emp.getId()))
                .sorted(Comparator
                        .comparing((Employee emp) -> emp.getLastWeekendWorked() == null ? LocalDate.MIN : emp.getLastWeekendWorked())
                        .thenComparing(Employee::getId))
                .collect(Collectors.toList());

        List<Employee> picked = pickWeekendWorkers(available, messages, date);

        DayPlan plan = new DayPlan(date, DayType.HOLIDAY);

        if (picked.size() >= 2) {
            List<List<Employee>> pairs = formPairs(picked);
            assignHolidayPair(pairs.get(0), plan, true, accumulator);
            if (pairs.size() > 1) {
                assignHolidayPair(pairs.get(1), plan, false, accumulator);
            }
        }

        return plan;
    }

    private List<Employee> pickWeekendWorkers(
            List<Employee> available,
            List<ValidationMessage> messages,
            LocalDate date) {

        List<Employee> farmaceuticas = available.stream()
                .filter(emp -> emp.getRole() == Role.F)
                .toList();

        List<Employee> tecnicas = available.stream()
                .filter(emp -> emp.getRole() == Role.T)
                .toList();

        if (farmaceuticas.isEmpty()) {
            messages.add(ValidationMessage.error(date, null,
                    "No farmacêuticas available for weekend/holiday"));
        } else if (farmaceuticas.size() < 2) {
            messages.add(ValidationMessage.warning(date, null,
                    "Only 1 farmacêutica available — one pair will lack F coverage"));
        }

        // Take up to 2 F first to guarantee one per pair
        int fCount = Math.min(2, farmaceuticas.size());
        List<Employee> picked = new ArrayList<>(farmaceuticas.subList(0, fCount));

        // Fill remaining spots from tecnicas, then extra farmaceuticas
        int remaining = ShiftTemplates.WEEKEND_WORKERS - picked.size();
        int tCount = Math.min(remaining, tecnicas.size());
        picked.addAll(tecnicas.subList(0, tCount));

        remaining = ShiftTemplates.WEEKEND_WORKERS - picked.size();
        if (remaining > 0 && farmaceuticas.size() > fCount) {
            int extraF = Math.min(remaining, farmaceuticas.size() - fCount);
            picked.addAll(farmaceuticas.subList(fCount, fCount + extraF));
        }

        return picked;
    }

    /**
     * Split picked workers into 2 pairs, each with at least 1 F where possible.
     */
    private List<List<Employee>> formPairs(List<Employee> picked) {
        List<Employee> fs = picked.stream()
                .filter(emp -> emp.getRole() == Role.F)
                .toList();
        List<Employee> ts = picked.stream()
                .filter(emp -> emp.getRole() == Role.T)
                .toList();

        List<Employee> pairA = new ArrayList<>();
        List<Employee> pairB = new ArrayList<>();

        // Distribute F across pairs
        if (!fs.isEmpty()) pairA.add(fs.get(0));
        if (fs.size() >= 2) pairB.add(fs.get(1));

        // Distribute remaining (T first, then extra F)
        List<Employee> rest = new ArrayList<>(ts);
        for (int i = 2; i < fs.size(); i++) {
            rest.add(fs.get(i));
        }

        for (Employee emp : rest) {
            if (pairA.size() <= pairB.size()) {
                pairA.add(emp);
            } else {
                pairB.add(emp);
            }
        }

        List<List<Employee>> pairs = new ArrayList<>();
        if (!pairA.isEmpty()) pairs.add(pairA);
        if (!pairB.isEmpty()) pairs.add(pairB);
        return pairs;
    }

    private void assignPairToWeekend(
            List<Employee> pair,
            DayPlan satPlan,
            DayPlan sunPlan,
            boolean satIsHoliday,
            boolean isPairA,
            WeekAccumulator accumulator) {

        LocalTime satStart, satEnd, sunStart, sunEnd;

        if (satIsHoliday) {
            // Both days use holiday template
            satStart = isPairA ? ShiftTemplates.SUN_MORNING_START : ShiftTemplates.SUN_EVENING_START;
            satEnd   = isPairA ? ShiftTemplates.SUN_MORNING_END   : ShiftTemplates.SUN_EVENING_END;
        } else {
            satStart = isPairA ? ShiftTemplates.SAT_MORNING_START : ShiftTemplates.SAT_EVENING_START;
            satEnd   = isPairA ? ShiftTemplates.SAT_MORNING_END   : ShiftTemplates.SAT_EVENING_END;
        }

        // Cross-link: Pair A gets Sat morning + Sun evening, Pair B gets Sat evening + Sun morning
        sunStart = isPairA ? ShiftTemplates.SUN_EVENING_START : ShiftTemplates.SUN_MORNING_START;
        sunEnd   = isPairA ? ShiftTemplates.SUN_EVENING_END   : ShiftTemplates.SUN_MORNING_END;

        for (Employee emp : pair) {
            SlotAssignment satSlot = new SlotAssignment(
                    emp.getId(), emp.getName(), emp.getRole(),
                    satPlan.getDate(), satStart, satEnd, null, null);
            satPlan.addAssignment(satSlot);
            accumulator.addHours(emp.getId(), satSlot.hoursWorked());

            SlotAssignment sunSlot = new SlotAssignment(
                    emp.getId(), emp.getName(), emp.getRole(),
                    sunPlan.getDate(), sunStart, sunEnd, null, null);
            sunPlan.addAssignment(sunSlot);
            accumulator.addHours(emp.getId(), sunSlot.hoursWorked());
        }
    }

    private void assignHolidayPair(
            List<Employee> pair,
            DayPlan plan,
            boolean isMorning,
            WeekAccumulator accumulator) {

        LocalTime start = isMorning ? ShiftTemplates.SUN_MORNING_START : ShiftTemplates.SUN_EVENING_START;
        LocalTime end   = isMorning ? ShiftTemplates.SUN_MORNING_END   : ShiftTemplates.SUN_EVENING_END;

        for (Employee emp : pair) {
            SlotAssignment slot = new SlotAssignment(
                    emp.getId(), emp.getName(), emp.getRole(),
                    plan.getDate(), start, end, null, null);
            plan.addAssignment(slot);
            accumulator.addHours(emp.getId(), slot.hoursWorked());
        }
    }
}