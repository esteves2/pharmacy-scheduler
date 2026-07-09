package com.farmacia.scheduler.engine;

import com.farmacia.scheduler.engine.model.*;
import com.farmacia.scheduler.model.Employee;
import com.farmacia.scheduler.model.Role;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public class WeekendAssigner {

    public List<DayPlan> assignWeekend(
            LocalDate saturday,
            LocalDate sunday,
            List<Employee> employees,
            Set<LocalDate> holidays,
            Set<Long> absentEmployeeIdsSat,
            Set<Long> absentEmployeeIdsSun,
            WeekAccumulator accumulator,
            List<ValidationMessage> messages,
            Map<Long, LocalDate> effectiveLastWeekendWorked,
            Map<Long, Boolean> lastWeekendWasPairA) {

        boolean satIsHoliday = holidays.contains(saturday);

        Set<Long> absentEither = new HashSet<>(absentEmployeeIdsSat);
        absentEither.addAll(absentEmployeeIdsSun);

        List<Employee> available = employees.stream()
                .filter(emp -> !absentEither.contains(emp.getId()))
                .sorted(Comparator
                        .comparing((Employee emp) -> effectiveLastWeekendWorked.getOrDefault(emp.getId(), LocalDate.MIN))
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
            List<List<Employee>> pairs = formPairs(picked, lastWeekendWasPairA);
            assignPairToWeekend(pairs.get(0), satPlan, sunPlan, satIsHoliday, true, accumulator);
            if (pairs.size() > 1) {
                assignPairToWeekend(pairs.get(1), satPlan, sunPlan, satIsHoliday, false, accumulator);
            }
        }

        return List.of(satPlan, sunPlan);
    }

    public DayPlan assignHoliday(
            LocalDate date,
            List<Employee> employees,
            Set<Long> absentEmployeeIds,
            WeekAccumulator accumulator,
            List<ValidationMessage> messages,
            Map<Long, LocalDate> effectiveLastWeekendWorked) {

        List<Employee> available = employees.stream()
                .filter(emp -> !absentEmployeeIds.contains(emp.getId()))
                .sorted(Comparator
                        .comparing((Employee emp) -> effectiveLastWeekendWorked.getOrDefault(emp.getId(), LocalDate.MIN))
                        .thenComparing(Employee::getId))
                .collect(Collectors.toList());

        List<Employee> picked = pickWeekendWorkers(available, messages, date);

        DayPlan plan = new DayPlan(date, DayType.HOLIDAY);

        if (picked.size() >= 2) {
            // Mid-week holidays don't cross-link Sat/Sun, so there's no A<->B to alternate.
            List<List<Employee>> pairs = formPairs(picked, Map.of());
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

        long fAvailable = available.stream().filter(emp -> emp.getRole() == Role.F).count();
        if (fAvailable == 0) {
            messages.add(ValidationMessage.error(date, null,
                    "No farmacêuticas available for weekend/holiday"));
        } else if (fAvailable < 2) {
            messages.add(ValidationMessage.warning(date, null,
                    "Only 1 farmacêutica available — one pair will lack F coverage"));
        }

        int target = ShiftTemplates.WEEKEND_WORKERS;
        int minF = ShiftTemplates.WEEKEND_PAIRS * ShiftTemplates.REQUIRED_FARMACEUTICAS_PER_PAIR;

        // Flat rotation: take the longest-waiting workers OVERALL, regardless of role
        // ('available' is pre-sorted longest-since-last-weekend first). This lets the larger
        // pharmacist pool keep pace with technicians — the real schedule runs 3F+1T about
        // one weekend in five, which evens everyone to ~2 weekends/month. See decision 023.
        List<Employee> picked = new ArrayList<>(available.subList(0, Math.min(target, available.size())));

        // Guarantee the F floor (one per pair): swap the most-recently-worked técnicas out
        // for the longest-waiting unpicked farmacêuticas until at least minF F's are in.
        Set<Long> pickedIds = picked.stream().map(Employee::getId).collect(Collectors.toSet());
        List<Employee> unpickedFs = available.stream()
                .filter(emp -> emp.getRole() == Role.F && !pickedIds.contains(emp.getId()))
                .toList();
        long fInPicked = picked.stream().filter(emp -> emp.getRole() == Role.F).count();
        int fi = 0;
        for (int i = picked.size() - 1; i >= 0 && fInPicked < minF && fi < unpickedFs.size(); i--) {
            if (picked.get(i).getRole() == Role.T) {
                picked.set(i, unpickedFs.get(fi++));
                fInPicked++;
            }
        }

        return picked;
    }

    private List<List<Employee>> formPairs(List<Employee> picked, Map<Long, Boolean> lastWeekendWasPairA) {
        List<Employee> pairA = new ArrayList<>();
        List<Employee> pairB = new ArrayList<>();

        // One F anchors each pair (guarantees a pharmacist on both shifts). Honor the
        // two anchors' A<->B flip when they want opposite sides; otherwise split arbitrarily.
        List<Employee> fs = picked.stream().filter(emp -> emp.getRole() == Role.F).toList();
        Employee fa = fs.isEmpty() ? null : fs.get(0);
        Employee fb = fs.size() >= 2 ? fs.get(1) : null;

        Set<Long> anchored = new HashSet<>();
        if (fa != null && fb != null) {
            if (wantsPairA(fa, lastWeekendWasPairA) && !wantsPairA(fb, lastWeekendWasPairA)) {
                pairA.add(fa); pairB.add(fb);
            } else if (!wantsPairA(fa, lastWeekendWasPairA) && wantsPairA(fb, lastWeekendWasPairA)) {
                pairA.add(fb); pairB.add(fa);
            } else {
                pairA.add(fa); pairB.add(fb);
            }
            anchored.add(fa.getId());
            anchored.add(fb.getId());
        } else if (fa != null) {
            pairA.add(fa);
            anchored.add(fa.getId());
        }

        // Distribute the remaining workers, honoring each one's flip where the pair has room.
        for (Employee emp : picked) {
            if (anchored.contains(emp.getId())) continue;
            boolean wantA = wantsPairA(emp, lastWeekendWasPairA);
            if (wantA && pairA.size() < 2) pairA.add(emp);
            else if (!wantA && pairB.size() < 2) pairB.add(emp);
            else if (pairA.size() < 2) pairA.add(emp);
            else pairB.add(emp);
        }

        List<List<Employee>> pairs = new ArrayList<>();
        if (!pairA.isEmpty()) pairs.add(pairA);
        if (!pairB.isEmpty()) pairs.add(pairB);
        return pairs;
    }

    /** Each worker flips: opposite of last weekend's side. No history defaults to Pair A. */
    private boolean wantsPairA(Employee emp, Map<Long, Boolean> lastWeekendWasPairA) {
        return !lastWeekendWasPairA.getOrDefault(emp.getId(), false);
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