package com.farmacia.scheduler.engine;

import com.farmacia.scheduler.engine.model.*;
import com.farmacia.scheduler.model.Employee;
import com.farmacia.scheduler.model.Role;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public class WeekdayFiller {

    /**
     * Fills one weekday.
     *
     * @param slotOwnerForWeek    mutable map (slot index → employee ID) shared across all weekday
     *                            calls for the same week. Ownership is recorded via putIfAbsent on
     *                            the first day a slot is filled, then respected on subsequent days.
     * @param hadBreakShiftLastWeek employees who worked a break-shift last week; they are
     *                            deprioritised for break slots (slots 2 and 3) this week.
     */
    public DayPlan fillWeekday(
            LocalDate date,
            DayType dayType,
            List<Employee> employees,
            Set<Long> absentEmployeeIds,
            WeekAccumulator accumulator,
            Map<Integer, Long> slotOwnerForWeek,
            Set<Long> hadBreakShiftLastWeek,
            Set<Long> weekendWorkerIds,
            Map<Long, Integer> breaksThisWeek,
            List<ValidationMessage> messages) {

        DayPlan plan = new DayPlan(date, dayType);
        LocalTime[][] slots = ShiftTemplates.WEEKDAY_SLOTS;

        for (int i = 0; i < slots.length; i++) {
            LocalTime[] slot = slots[i];
            LocalTime start      = slot[0];
            LocalTime end        = slot[1];
            LocalTime breakStart = slot[2];
            LocalTime breakEnd   = slot[3];

            Employee pick = pickEmployee(
                    i, employees, absentEmployeeIds, plan, accumulator,
                    breakStart, slotOwnerForWeek, hadBreakShiftLastWeek, weekendWorkerIds);

            if (pick == null) {
                // Slots 0-5 are essential; 6-7 are optional overflow. Only warn when an
                // essential slot cannot be filled — empty overflow under low staffing is
                // expected, and the validator's headcount checks catch real coverage gaps.
                if (i < 6) {
                    messages.add(ValidationMessage.warning(date, start.getHour(),
                            "Could not fill slot " + start + "-" + end + " (no available employees)"));
                }
                continue;
            }

            // Record slot ownership the first time this slot is filled this week.
            slotOwnerForWeek.putIfAbsent(i, pick.getId());

            // --- Weekly break budget (max 2 lunch breaks per person per week) ---
            // Keep the worker in the same window all week, but only let them take the
            // lunch break twice. Past the budget, swap the break shift for the no-break
            // variant of the same window — for the late slot that's 11-19 (still covers
            // the 18-19 shoulder), for the 09-18 midday slot that's 09-17. See decision 019.
            LocalTime aStart = start, aEnd = end, aBrkStart = breakStart, aBrkEnd = breakEnd;
            if (breakStart != null) {
                int used = breaksThisWeek.getOrDefault(pick.getId(), 0);
                if (used >= 2) {
                    aBrkStart = null;
                    aBrkEnd = null;
                    if (start.getHour() == 10) {            // 10-19 break -> 11-19 straight
                        aStart = LocalTime.of(11, 0);
                        aEnd = LocalTime.of(19, 0);
                    } else if (start.getHour() == 9) {       // 09-18 break -> 09-17 straight
                        aEnd = LocalTime.of(17, 0);
                    }
                } else {
                    breaksThisWeek.put(pick.getId(), used + 1);
                }
            }

            SlotAssignment assignment = new SlotAssignment(
                    pick.getId(), pick.getName(), pick.getRole(),
                    date, aStart, aEnd, aBrkStart, aBrkEnd);
            plan.addAssignment(assignment);
            accumulator.addHours(pick.getId(), assignment.hoursWorked());
        }

        // --- Overstaffing: absorb every otherwise-idle worker with an extra midday shift ---
        // Scales to any staff count, so the app needs no rebuild when someone goes on leave
        // or returns (e.g. maternity). The real schedules never add a third morning or
        // evening — spare bodies always land in the midday band — so each extra cycles
        // through the three common straight (no-break) midday shifts. Fires only for workers
        // still unassigned today (true overstaffing) whose extra 8h keeps them <= 40h, most
        // under-allocated first. See decisions 017 and 020.
        for (int placed = 0; ; placed++) {
            Employee extra = employees.stream()
                    .filter(emp -> !absentEmployeeIds.contains(emp.getId()))
                    .filter(emp -> !plan.hasEmployee(emp.getId()))
                    .filter(emp -> accumulator.getWeeklyHours(emp.getId()) + 8
                            <= ShiftTemplates.OVERTIME_THRESHOLD_HOURS)
                    .min(Comparator
                            .comparingDouble((Employee emp) ->
                                    accumulator.getPriorWeeksHours(emp.getId())
                                    + accumulator.getWeeklyHours(emp.getId()))
                            .thenComparingLong(Employee::getId))
                    .orElse(null);
            if (extra == null) break;
            LocalTime[] shift = EXTRA_MIDDAY_SHIFTS[placed % EXTRA_MIDDAY_SHIFTS.length];
            SlotAssignment assignment = new SlotAssignment(
                    extra.getId(), extra.getName(), extra.getRole(),
                    date, shift[0], shift[1], null, null);
            plan.addAssignment(assignment);
            accumulator.addHours(extra.getId(), assignment.hoursWorked());
        }

        return plan;
    }

    // Straight (no-break) midday shifts used for overstaffing extras, in the proportions
    // the real Excel uses them. No lunch break, so they never affect the break budget.
    private static final LocalTime[][] EXTRA_MIDDAY_SHIFTS = {
            { LocalTime.of(9, 0),  LocalTime.of(17, 0) },
            { LocalTime.of(11, 0), LocalTime.of(19, 0) },
            { LocalTime.of(10, 0), LocalTime.of(18, 0) },
    };

    private Employee pickEmployee(
            int slotIndex,
            List<Employee> employees,
            Set<Long> absentEmployeeIds,
            DayPlan plan,
            WeekAccumulator accumulator,
            LocalTime breakStart,
            Map<Integer, Long> slotOwnerForWeek,
            Set<Long> hadBreakShiftLastWeek,
            Set<Long> weekendWorkerIds) {

        // --- Same-slot-all-week: if a slot owner was recorded from a prior day, use them first ---
        Long ownerId = slotOwnerForWeek.get(slotIndex);
        if (ownerId != null && !absentEmployeeIds.contains(ownerId) && !plan.hasEmployee(ownerId)) {
            return employees.stream()
                    .filter(emp -> emp.getId().equals(ownerId))
                    .findFirst()
                    .orElse(null);
        }

        // --- Build candidate pool ---
        // Exclude: absent, already working today, and employees whose slot is owned by a different slot
        // (they should be available for their own slot, not stolen by this one).
        Set<Long> ownedElsewhere = new HashSet<>();
        for (Map.Entry<Integer, Long> entry : slotOwnerForWeek.entrySet()) {
            if (entry.getKey() != slotIndex) {
                ownedElsewhere.add(entry.getValue());
            }
        }

        List<Employee> candidates = employees.stream()
                .filter(emp -> !absentEmployeeIds.contains(emp.getId()))
                .filter(emp -> !plan.hasEmployee(emp.getId()))
                .filter(emp -> !ownedElsewhere.contains(emp.getId()))
                .sorted(Comparator
                        .comparingDouble((Employee emp) ->
                                accumulator.getPriorWeeksHours(emp.getId())
                                + accumulator.getWeeklyHours(emp.getId()))
                        .thenComparingLong(Employee::getId))
                .toList();

        // If no candidates (all owned by other slots or absent), relax the ownedElsewhere constraint.
        if (candidates.isEmpty()) {
            candidates = employees.stream()
                    .filter(emp -> !absentEmployeeIds.contains(emp.getId()))
                    .filter(emp -> !plan.hasEmployee(emp.getId()))
                    .sorted(Comparator
                            .comparingDouble((Employee emp) ->
                                    accumulator.getPriorWeeksHours(emp.getId())
                                    + accumulator.getWeeklyHours(emp.getId()))
                            .thenComparingLong(Employee::getId))
                    .toList();
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // --- No-consecutive-break-shift rule ---
        // For break slots (breakStart != null), prefer candidates who did NOT have a break shift last week.
        List<Employee> finalCandidates = candidates;
        if (breakStart != null && !hadBreakShiftLastWeek.isEmpty()) {
            List<Employee> noBreakVeterans = candidates.stream()
                    .filter(emp -> !hadBreakShiftLastWeek.contains(emp.getId()))
                    .toList();
            if (!noBreakVeterans.isEmpty()) {
                finalCandidates = noBreakVeterans;
            }
        }

        // --- F-coverage invariant, anchored on non-weekend pharmacists ---
        // Every open hour needs at least one F. With the fixed weekday templates this
        // reduces to two mandatory groups: an F in {slot0, slot1} (08-16, the only cover
        // of the 08:00 opening hour) and an F in {slot4, slot5} (14-22, the only cover of
        // the 19-22 closing block). A morning F plus an evening F span the whole 08-22 day.
        // Prefer pharmacists NOT working this weekend as the anchors, so their slots stay
        // covered on the weekend-workers' folga days; reserve one stable F for the evening.
        boolean inMorningGroup = (slotIndex == 0 || slotIndex == 1);
        boolean inEveningGroup = (slotIndex == 4 || slotIndex == 5);
        boolean morningHasF = plan.farmaceuticasAtHour(8) > 0;   // opening hour
        boolean eveningHasF = plan.farmaceuticasAtHour(21) > 0;  // closing hour

        List<Employee> stableFs = finalCandidates.stream()
                .filter(emp -> emp.getRole() == Role.F && !weekendWorkerIds.contains(emp.getId()))
                .toList();
        List<Employee> anyFs = finalCandidates.stream()
                .filter(emp -> emp.getRole() == Role.F)
                .toList();

        // Keep a stable (non-weekend) F for the evening anchor while it is still ahead.
        int reserve = 0;
        if (!eveningHasF && !inEveningGroup && slotIndex < 4) reserve++;

        // The last slot able to satisfy an unmet group must take an F.
        boolean lastChanceMorning = inMorningGroup && !morningHasF && slotIndex == 1;
        boolean lastChanceEvening = inEveningGroup && !eveningHasF && slotIndex == 5;

        // Take an F for a mandatory slot when it is the last chance, or when satisfying
        // the group now still leaves a stable F for the evening anchor.
        boolean wantF = lastChanceMorning || lastChanceEvening
                || (inMorningGroup && !morningHasF && stableFs.size() - 1 >= reserve)
                || (inEveningGroup && !eveningHasF);

        if (wantF) {
            if (!stableFs.isEmpty()) return stableFs.get(0);   // prefer a non-weekend F
            if (!anyFs.isEmpty()) return anyFs.get(0);         // fall back to any F
        }

        // Non-mandatory or already-satisfied slot: do not spend a stable F that the evening
        // anchor still needs — hand the slot to anyone who is not a reserved stable F.
        Employee best = finalCandidates.getFirst();
        boolean bestIsStableF = best.getRole() == Role.F && !weekendWorkerIds.contains(best.getId());
        if (bestIsStableF && stableFs.size() <= reserve) {
            Optional<Employee> alt = finalCandidates.stream()
                    .filter(emp -> !(emp.getRole() == Role.F && !weekendWorkerIds.contains(emp.getId())))
                    .findFirst();
            if (alt.isPresent()) {
                return alt.get();
            }
        }
        return best;
    }
}
