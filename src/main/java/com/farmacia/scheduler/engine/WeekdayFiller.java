package com.farmacia.scheduler.engine;

import com.farmacia.scheduler.engine.model.*;
import com.farmacia.scheduler.model.Employee;
import com.farmacia.scheduler.model.Role;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public class WeekdayFiller {

    /**
     * Phase 2: Fill a normal weekday (Mon-Sat, no holiday).
     * Applies the 6-slot template. Picks employees sorted by
     * priorWeeksHours ASC, weeklyHours ASC, id ASC.
     */
    public DayPlan fillWeekday(
            LocalDate date,
            DayType dayType,
            List<Employee> employees,
            Set<Long> absentEmployeeIds,
            WeekAccumulator accumulator,
            List<ValidationMessage> messages) {

        DayPlan plan = new DayPlan(date, dayType);
        LocalTime[][] slots = ShiftTemplates.WEEKDAY_SLOTS;

        for (int i = 0; i < slots.length; i++) {
            LocalTime[] slot = slots[i];
            LocalTime start = slot[0];
            LocalTime end = slot[1];
            LocalTime breakStart = slot[2];
            LocalTime breakEnd = slot[3];

            LocalTime[][] remainingSlots = Arrays.copyOfRange(slots, i + 1, slots.length);

            Employee pick = pickEmployee(employees, absentEmployeeIds, plan, accumulator,
                    start, end, breakStart, breakEnd, remainingSlots);

            if (pick == null) {
                messages.add(ValidationMessage.warning(date, start.getHour(),
                        "Could not fill slot " + start + "-" + end + " (no available employees)"));
                continue;
            }

            SlotAssignment assignment = new SlotAssignment(
                    pick.getId(), pick.getName(), pick.getRole(),
                    date, start, end, breakStart, breakEnd);
            plan.addAssignment(assignment);
            accumulator.addHours(pick.getId(), assignment.hoursWorked());
        }

        return plan;
    }

    private Employee pickEmployee(
            List<Employee> employees,
            Set<Long> absentEmployeeIds,
            DayPlan plan,
            WeekAccumulator accumulator,
            LocalTime start,
            LocalTime end,
            LocalTime breakStart,
            LocalTime breakEnd,
            LocalTime[][] remainingSlots) {

        List<Employee> candidates = employees.stream()
                .filter(emp -> !absentEmployeeIds.contains(emp.getId()))
                .filter(emp -> !plan.hasEmployee(emp.getId()))
                .sorted(Comparator
                        .comparingDouble((Employee emp) -> accumulator.getPriorWeeksHours(emp.getId()))
                        .thenComparingDouble(emp -> accumulator.getWeeklyHours(emp.getId()))
                        .thenComparingLong(Employee::getId))
                .toList();

        if (candidates.isEmpty()) {
            return null;
        }

        // F-priority if:
        //   (a) this slot would leave an F-less hour in the current plan, OR
        //   (b) remaining slots can't cover the hours this slot leaves open.
        boolean needsF = needsFarmaceuticaNow(plan, start, end, breakStart, breakEnd, remainingSlots)
                || mustReserveFarmaceuticaForLaterSlots(plan, candidates, remainingSlots);

        if (needsF) {
            Optional<Employee> farmaceutica = candidates.stream()
                    .filter(emp -> emp.getRole() == Role.F)
                    .findFirst();
            if (farmaceutica.isPresent()) {
                return farmaceutica.get();
            }
        }

        return candidates.getFirst();
    }

    /**
     * True if any hour this candidate slot spans currently has no F coverage
     * AND no remaining slot can cover that same hour.
     */
    private boolean needsFarmaceuticaNow(
            DayPlan plan,
            LocalTime start,
            LocalTime end,
            LocalTime breakStart,
            LocalTime breakEnd,
            LocalTime[][] remainingSlots) {

        for (int hour = start.getHour(); hour < end.getHour(); hour++) {
            if (breakStart != null && hour >= breakStart.getHour() && hour < breakEnd.getHour()) {
                continue;
            }
            if (plan.farmaceuticasAtHour(hour) == 0) {
                boolean coveredLater = false;
                for (LocalTime[] rem : remainingSlots) {
                    LocalTime rStart = rem[0], rEnd = rem[1], rBrkS = rem[2], rBrkE = rem[3];
                    if (hour >= rStart.getHour() && hour < rEnd.getHour()) {
                        if (rBrkS == null || hour < rBrkS.getHour() || hour >= rBrkE.getHour()) {
                            coveredLater = true;
                            break;
                        }
                    }
                }
                if (!coveredLater) return true;
            }
        }
        return false;
    }

    /**
     * Lookahead. Greedy set cover over the uncovered hours across remaining slots.
     * Returns true if the minimum number of Fs needed to cover those hours
     * exceeds availableFs - 1 (i.e. spending one F here would leave later slots
     * unable to get full coverage).
     */
    private boolean mustReserveFarmaceuticaForLaterSlots(
            DayPlan plan,
            List<Employee> candidates,
            LocalTime[][] remainingSlots) {

        if (remainingSlots.length == 0) return false;

        long availableFs = candidates.stream().filter(emp -> emp.getRole() == Role.F).count();
        if (availableFs == 0) return false;

        // Collect the union of uncovered hours that the remaining slots span.
        Set<Integer> uncovered = new HashSet<>();
        for (LocalTime[] slot : remainingSlots) {
            LocalTime rStart = slot[0], rEnd = slot[1], rBrkS = slot[2], rBrkE = slot[3];
            for (int h = rStart.getHour(); h < rEnd.getHour(); h++) {
                if (rBrkS != null && h >= rBrkS.getHour() && h < rBrkE.getHour()) continue;
                if (plan.farmaceuticasAtHour(h) == 0) uncovered.add(h);
            }
        }

        if (uncovered.isEmpty()) return false;

        // For each remaining slot, precompute which uncovered hours it can cover.
        List<Set<Integer>> coverage = new ArrayList<>();
        for (LocalTime[] slot : remainingSlots) {
            LocalTime rStart = slot[0], rEnd = slot[1], rBrkS = slot[2], rBrkE = slot[3];
            Set<Integer> covers = new HashSet<>();
            for (int h : uncovered) {
                if (h >= rStart.getHour() && h < rEnd.getHour()) {
                    if (rBrkS == null || h < rBrkS.getHour() || h >= rBrkE.getHour()) covers.add(h);
                }
            }
            if (!covers.isEmpty()) coverage.add(covers);
        }

        // Greedy set cover — count the minimum number of slots (Fs) needed.
        Set<Integer> remaining = new HashSet<>(uncovered);
        int fsNeeded = 0;
        while (!remaining.isEmpty()) {
            int bestCount = 0;
            Set<Integer> bestIntersection = null;
            for (Set<Integer> covers : coverage) {
                Set<Integer> intersection = new HashSet<>(covers);
                intersection.retainAll(remaining);
                if (intersection.size() > bestCount) {
                    bestCount = intersection.size();
                    bestIntersection = intersection;
                }
            }
            if (bestIntersection == null) break; // remaining hours can't be covered by any slot
            remaining.removeAll(bestIntersection);
            fsNeeded++;
        }

        return fsNeeded > availableFs - 1;
    }
}