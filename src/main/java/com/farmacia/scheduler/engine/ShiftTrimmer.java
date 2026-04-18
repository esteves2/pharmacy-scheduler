package com.farmacia.scheduler.engine;

import com.farmacia.scheduler.engine.model.*;
import com.farmacia.scheduler.model.Role;

import java.util.*;

public class ShiftTrimmer {

    private static final double WEEKLY_HOURS_CAP = 40.0;

    /**
     * Phase 3: Trim overtime.
     * Process most-over-target employee first. For each, find assignments
     * safe to remove (headcount stays >= minimum, F constraint preserved).
     * Iterative — recomputes after every removal.
     */
    public void trim(
            List<DayPlan> days,
            WeekAccumulator accumulator,
            List<ValidationMessage> messages,
            Map<Long, String> idToName) {

        boolean changed = true;
        while (changed) {
            changed = false;

            List<Long> overTarget = accumulator.getAllWeeklyHours().entrySet().stream()
                    .filter(entry -> entry.getValue() > WEEKLY_HOURS_CAP)
                    .sorted(Map.Entry.<Long, Double>comparingByValue().reversed()
                            .thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey)
                    .toList();

            if (overTarget.isEmpty()) {
                return;
            }

            for (Long employeeId : overTarget) {
                double currentHours = accumulator.getWeeklyHours(employeeId);
                SlotAssignment removed = removeBestCandidate(employeeId, currentHours, days);
                if (removed != null) {
                    accumulator.removeHours(employeeId, removed.hoursWorked());
                    changed = true;
                    break; // restart outer loop — recompute over-target list
                }
            }

            // If no employee could be trimmed, surface warnings
            if (!changed) {
                for (Long employeeId : overTarget) {
                    String name = idToName.getOrDefault(employeeId, "Employee " + employeeId);
                    messages.add(ValidationMessage.warning(null, null,
                            name + " exceeds 40h but cannot be safely trimmed"));
                }
            }
        }
    }

    /**
     * Find the best assignment to remove for this employee.
     * Safe = headcount stays >= minimum, F coverage preserved.
     * Best = highest hours saved.
     */
    private SlotAssignment removeBestCandidate(long employeeId, double currentHours, List<DayPlan> days) {
        double excess = currentHours - WEEKLY_HOURS_CAP;

        List<Candidate> safeCandidates = new ArrayList<>();
        for (DayPlan day : days) {
            for (SlotAssignment slot : day.getAssignments()) {
                if (slot.getEmployeeId() != employeeId) continue;
                if (!isSafeToRemove(slot, day)) continue;
                safeCandidates.add(new Candidate(slot, day));
            }
        }

        if (safeCandidates.isEmpty()) {
            return null;
        }

        // Prefer the smallest removal that covers the excess (brings employee to ≤40h).
        Optional<Candidate> sufficient = safeCandidates.stream()
                .filter(c -> c.slot.hoursWorked() >= excess)
                .min(Comparator.comparingDouble(c -> c.slot.hoursWorked()));

        Candidate chosen = sufficient.orElseGet(() ->
                // Nothing covers the excess alone — take the largest available
                // to make maximum progress toward 40h.
                safeCandidates.stream()
                        .max(Comparator.comparingDouble(c -> c.slot.hoursWorked()))
                        .orElseThrow()
        );

        chosen.day.removeAssignment(chosen.slot);
        return chosen.slot;
    }

    private record Candidate(SlotAssignment slot, DayPlan day) {}

    /**
     * Safe if every hour this slot covers still meets minimum headcount
     * AND retains at least 1 F after removal.
     */
    private boolean isSafeToRemove(SlotAssignment slot, DayPlan day) {
        for (int hour = slot.getStartTime().getHour(); hour < slot.getEndTime().getHour(); hour++) {
            if (slot.getBreakStart() != null
                    && hour >= slot.getBreakStart().getHour()
                    && hour < slot.getBreakEnd().getHour()) {
                continue;
            }

            int currentHeadcount = day.headcountAtHour(hour);
            int minimumForHour = minimumHeadcount(hour, day.getDayType());
            if (currentHeadcount - 1 < minimumForHour) {
                return false;
            }

            if (slot.getEmployeeRole() == Role.F) {
                if (day.farmaceuticasAtHour(hour) - 1 < 1) {
                    return false;
                }
            }
        }
        return true;
    }

    private int minimumHeadcount(int hour, DayType dayType) {
        if (dayType != DayType.WEEKDAY) return 2;
        if (hour < 10) return 2;
        if (hour < 19) return 3;
        return 2;
    }
}