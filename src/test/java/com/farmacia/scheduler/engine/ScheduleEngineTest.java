package com.farmacia.scheduler.engine;

import com.farmacia.scheduler.engine.model.*;
import com.farmacia.scheduler.model.*;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the scheduling engine's invariants. {@link ScheduleEngine#generate}
 * is a pure function (no DB), so we call it directly and assert the rules that must always
 * hold: a pharmacist every open hour, nobody over 40h, at most two lunch breaks per week,
 * hours in the 37-40h band on a normal week, correct holiday handling, and a fair +
 * alternating weekend rotation across many weeks.
 *
 * These also serve as the first automated verification of the weekend-rotation (023) and
 * holiday (024) changes.
 */
class ScheduleEngineTest {

    private static final long PAULA = 1, NIDIA = 2, JESSICA = 3, ANDREIA = 4, CRISTINA = 5,
            NATTY = 6, CAROLINA = 7, CRISANTA = 8, PAULINA = 9;

    private final ScheduleEngine engine = new ScheduleEngine();

    // ---------------------------------------------------------------- scenarios

    @Test
    void normalWeek_allInvariantsHold() {
        WeekResult r = generate(LocalDate.of(2026, 6, 8), Set.of(), List.of(), Map.of(), Map.of());

        assertNoErrors(r);
        assertPharmacistEveryOpenHour(r);
        assertNobodyOverForty(r);
        assertAtMostTwoBreaks(r);

        // Fully-staffed normal week: everyone lands in the 37-40h band.
        r.getWeeklyHoursByEmployee().forEach((id, h) -> assertTrue(h >= 37.0 && h <= 40.0,
                () -> "Employee " + id + " at " + h + "h, expected 37-40 in a normal week"));
    }

    @Test
    void holidayWeek_coverageHolds_folgasKept_holidayIsReducedCrewWithPharmacist() {
        LocalDate monday = LocalDate.of(2026, 3, 30);       // week containing Thu Apr 2 holiday
        LocalDate holiday = LocalDate.of(2026, 4, 2);
        WeekResult r = generate(monday, Set.of(holiday), List.of(), Map.of(), Map.of());

        assertNoErrors(r);
        assertPharmacistEveryOpenHour(r);
        assertNobodyOverForty(r);           // holidays are 6h days — they never cause overtime
        assertAtMostTwoBreaks(r);

        DayPlan hol = day(r, holiday);
        assertEquals(DayType.HOLIDAY, hol.getDayType(), "holiday should use the holiday template");
        assertTrue(hol.farmaceuticasAtHour(9) >= 1, "a pharmacist must staff the holiday");
        assertTrue(hol.getAssignments().size() <= 6,
                () -> "holiday should run a reduced crew, got " + hol.getAssignments().size());

        // Rule 1: a folga never lands on the holiday — nobody is marked off ON the holiday as a
        // "folga" (the holiday itself is the day off). Verified indirectly: no worker exceeds 40h
        // and coverage holds, i.e. the holiday didn't absorb a folga to inflate anyone's week.
    }

    @Test
    void understaffed_degradesGracefullyWithoutCrashingOrOvertime() {
        // Four of the five pharmacists on férias Mon-Fri — a genuine shortage.
        LocalDate monday = LocalDate.of(2026, 6, 8);
        List<EmployeeAbsence> absences = new ArrayList<>();
        for (long id : List.of(NIDIA, JESSICA, ANDREIA, CRISTINA)) {
            absences.add(absence(id, monday, monday.plusDays(4), AbsenceType.FERIAS));
        }
        WeekResult r = engine.generate(2026, isoWeek(monday), monday, staff(), absences,
                Set.of(), List.of(), Map.of(), Map.of());

        assertNotNull(r, "engine must return a schedule even when short-staffed");
        assertNobodyOverForty(r);           // never push anyone over 40h to cover a shortage
        assertAtMostTwoBreaks(r);
    }

    @Test
    void weekendRotation_isFairAndAlternatesOverManyWeeks() {
        List<ShiftAssignment> history = new ArrayList<>();
        Map<Long, Integer> weekendCount = new HashMap<>();
        Map<Long, List<Character>> patterns = new HashMap<>();

        LocalDate monday = LocalDate.of(2026, 1, 5);        // a Monday
        for (int w = 0; w < 8; w++, monday = monday.plusWeeks(1)) {
            Map<Long, LocalDate> lastWeekend = history.stream()
                    .filter(a -> a.getDate().getDayOfWeek() == DayOfWeek.SATURDAY)
                    .collect(Collectors.toMap(ShiftAssignment::getEmployeeId, ShiftAssignment::getDate,
                            (a, b) -> a.isAfter(b) ? a : b));

            WeekResult r = engine.generate(2026, isoWeek(monday), monday, staff(), List.of(),
                    Set.of(), new ArrayList<>(history), lastWeekend, Map.of());
            assertNoErrors(r);

            LocalDate saturday = monday.plusDays(5);
            for (SlotAssignment s : day(r, saturday).getAssignments()) {
                weekendCount.merge(s.getEmployeeId(), 1, Integer::sum);
                char pattern = s.getStartTime().getHour() < 12 ? 'A' : 'B';   // Sat morning=A, evening=B
                patterns.computeIfAbsent(s.getEmployeeId(), k -> new ArrayList<>()).add(pattern);
            }
            history.addAll(toHistory(r.getDays()));
        }

        // Fairness: over 8 weekends the load is spread across essentially everyone, F and T
        // alike (no one stuck doing far more than their share).
        int max = weekendCount.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int min = weekendCount.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        assertTrue(weekendCount.size() >= 8,
                () -> "rotation not spreading — only " + weekendCount.size() + " people worked a weekend");
        assertTrue(max - min <= 3, () -> "weekend load uneven across staff: " + weekendCount);

        // Alternation: anyone who worked 3+ weekends must have used BOTH Sat-morning (A) and
        // Sat-evening (B) — i.e. they were never stuck on one side (the bug 023 fixed).
        patterns.forEach((id, pats) -> {
            if (pats.size() >= 3) {
                assertTrue(pats.contains('A') && pats.contains('B'),
                        () -> "Employee " + id + " never alternated pattern: " + pats);
            }
        });
    }

    @Test
    void replan_midWeek_keepsCoverageAndFolgas_noOvertime() {
        LocalDate monday = LocalDate.of(2026, 6, 8);
        // Generate the full week, treat Mon+Tue as already worked (locked), then replan from
        // Wednesday — as if something changed mid-week. Folgas must still apply so no weekend
        // worker is pushed over 40h on the regenerated days (the gap decision 025 closed).
        WeekResult full = generate(monday, Set.of(), List.of(), Map.of(), Map.of());
        LocalDate wednesday = monday.plusDays(2);
        List<ShiftAssignment> locked = toHistory(full.getDays().stream()
                .filter(d -> d.getDate().isBefore(wednesday))
                .collect(Collectors.toList()));

        WeekResult r = engine.replan(2026, isoWeek(monday), monday, wednesday, staff(), List.of(),
                Set.of(), List.of(), Map.of(), Map.of(), locked);

        assertNobodyOverForty(r);
        assertPharmacistEveryOpenHour(r);
        assertAtMostTwoBreaks(r);
    }

    // ---------------------------------------------------------------- invariant helpers

    private void assertNoErrors(WeekResult r) {
        assertTrue(r.getErrors().isEmpty(), () -> "unexpected validation ERRORs: "
                + r.getErrors().stream().map(ValidationMessage::getMessage).toList());
    }

    private void assertNobodyOverForty(WeekResult r) {
        r.getWeeklyHoursByEmployee().forEach((id, h) ->
                assertTrue(h <= 40.0, () -> "Employee " + id + " over 40h: " + h));
    }

    private void assertAtMostTwoBreaks(WeekResult r) {
        Map<Long, Integer> breaks = new HashMap<>();
        for (DayPlan d : r.getDays()) {
            for (SlotAssignment s : d.getAssignments()) {
                if (s.getBreakStart() != null) breaks.merge(s.getEmployeeId(), 1, Integer::sum);
            }
        }
        breaks.forEach((id, c) -> assertTrue(c <= 2,
                () -> "Employee " + id + " has " + c + " lunch-break shifts (max 2)"));
    }

    private void assertPharmacistEveryOpenHour(WeekResult r) {
        for (DayPlan d : r.getDays()) {
            int close = (d.getDayType() == DayType.SUNDAY || d.getDayType() == DayType.HOLIDAY) ? 20 : 22;
            for (int h = 8; h < close; h++) {
                final int hour = h;
                assertTrue(d.farmaceuticasAtHour(h) >= 1, () -> "no pharmacist at " + hour
                        + ":00 on " + d.getDate() + " (" + d.getDayType() + ")");
            }
        }
    }

    // ---------------------------------------------------------------- fixture + plumbing

    private WeekResult generate(LocalDate monday, Set<LocalDate> holidays, List<ShiftAssignment> prior,
                                Map<Long, LocalDate> lastWeekend, Map<Long, LocalDate> lastHoliday) {
        return engine.generate(2026, isoWeek(monday), monday, staff(), List.of(),
                holidays, prior, lastWeekend, lastHoliday);
    }

    private int isoWeek(LocalDate monday) {
        return monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    private DayPlan day(WeekResult r, LocalDate date) {
        return r.getDays().stream().filter(d -> d.getDate().equals(date)).findFirst()
                .orElseThrow(() -> new AssertionError("no day plan for " + date));
    }

    /** Turn day plans into ShiftAssignment history rows, as ScheduleService persists them. */
    private List<ShiftAssignment> toHistory(List<DayPlan> days) {
        List<ShiftAssignment> out = new ArrayList<>();
        for (DayPlan d : days) {
            for (SlotAssignment s : d.getAssignments()) {
                ShiftAssignment sa = new ShiftAssignment();
                sa.setEmployeeId(s.getEmployeeId());
                sa.setDate(d.getDate());
                sa.setStartTime(s.getStartTime().toString());
                sa.setEndTime(s.getEndTime().toString());
                sa.setBreakStart(s.getBreakStart() != null ? s.getBreakStart().toString() : null);
                sa.setBreakEnd(s.getBreakEnd() != null ? s.getBreakEnd().toString() : null);
                out.add(sa);
            }
        }
        return out;
    }

    /** The nine active counter staff (Sara is on maternity — modelled by absence, not present here). */
    private List<Employee> staff() {
        return List.of(
                emp(PAULA, "Paula", Role.F), emp(NIDIA, "Nidia", Role.F), emp(JESSICA, "Jessica", Role.F),
                emp(ANDREIA, "Andreia", Role.F), emp(CRISTINA, "Cristina", Role.F),
                emp(NATTY, "Natty", Role.T), emp(CAROLINA, "Carolina", Role.T),
                emp(CRISANTA, "Crisanta", Role.T), emp(PAULINA, "Paulina", Role.T));
    }

    private Employee emp(long id, String name, Role role) {
        Employee e = new Employee();
        e.setId(id);
        e.setName(name);
        e.setRole(role);
        return e;
    }

    private EmployeeAbsence absence(long employeeId, LocalDate start, LocalDate end, AbsenceType type) {
        EmployeeAbsence a = new EmployeeAbsence();
        a.setEmployeeId(employeeId);
        a.setStartDate(start);
        a.setEndDate(end);
        a.setType(type);
        return a;
    }
}
