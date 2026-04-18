package com.farmacia.scheduler.engine;

import com.farmacia.scheduler.engine.model.*;
import com.farmacia.scheduler.model.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

public class EngineSmokeTest {

    // Stable employee IDs
    private static final long ID_PAULA    = 1L;
    private static final long ID_NIDIA    = 2L;
    private static final long ID_JESSICA  = 3L;
    private static final long ID_ANDREIA  = 4L;
    private static final long ID_CRISTINA = 5L;
    private static final long ID_NATTY    = 6L;
    private static final long ID_CAROLINA = 7L;
    private static final long ID_CRISANTA = 8L;
    private static final long ID_PAULINA  = 9L;
    private static final long ID_SARA     = 10L;

    public static void main(String[] args) {
        ScheduleEngine engine = new ScheduleEngine();

        // SCENARIO A — normal week, no holidays. ISO 2026-W24, monday 2026-06-08
        System.out.println("=".repeat(72));
        System.out.println("SCENARIO A — normal week, no holidays, no absences (besides Sara). ISO week 2026-W24");
        System.out.println("=".repeat(72));
        {
            LocalDate monday = LocalDate.of(2026, 6, 8);
            List<Employee> employees = buildActiveEmployees();
            List<EmployeeAbsence> absences = List.of(absenceFor(ID_SARA,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), AbsenceType.MATERNITY));
            WeekResult result = engine.generate(2026, 24, monday, employees, absences, Set.of(), List.of());
            printResult(result, employees, monday);
        }

        System.out.println();

        // SCENARIO B — holiday week. Mon 2026-03-30, holiday Thu 2026-04-02 (Dia da Autonomia)
        System.out.println("=".repeat(72));
        System.out.println("SCENARIO B — week containing a mid-week holiday (Apr 2, 2026 — Dia da Autonomia, a Thursday)");
        System.out.println("=".repeat(72));
        {
            LocalDate monday = LocalDate.of(2026, 3, 30);
            int isoWeek = monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            List<Employee> employees = buildActiveEmployees();
            List<EmployeeAbsence> absences = List.of(absenceFor(ID_SARA,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), AbsenceType.MATERNITY));
            WeekResult result = engine.generate(2026, isoWeek, monday, employees, absences,
                    Set.of(LocalDate.of(2026, 4, 2)), List.of());
            printResult(result, employees, monday);
        }

        System.out.println();

        // SCENARIO C — W24 with Andreia (Mon-Fri) and Crisanta (Mon-Wed) on FERIAS
        System.out.println("=".repeat(72));
        System.out.println("SCENARIO C — same as A but with two workers on FERIAS (Andreia Mon–Fri, Crisanta Mon–Wed)");
        System.out.println("=".repeat(72));
        {
            LocalDate monday = LocalDate.of(2026, 6, 8);
            List<Employee> employees = buildActiveEmployees();
            List<EmployeeAbsence> absences = List.of(
                    absenceFor(ID_SARA,     LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), AbsenceType.MATERNITY),
                    absenceFor(ID_ANDREIA,  monday, monday.plusDays(4), AbsenceType.FERIAS),  // Mon-Fri
                    absenceFor(ID_CRISANTA, monday, monday.plusDays(2), AbsenceType.FERIAS)   // Mon-Wed
            );
            WeekResult result = engine.generate(2026, 24, monday, employees, absences, Set.of(), List.of());
            printResult(result, employees, monday);
        }

        System.out.println();

        // SCENARIO D — Saturday is itself a holiday: Dec 26, 2026 (Santo Estêvão)
        System.out.println("=".repeat(72));
        System.out.println("SCENARIO D — Saturday is a holiday (2026-12-26, Santo Estêvão). Monday 2026-12-21");
        System.out.println("=".repeat(72));
        {
            LocalDate monday = LocalDate.of(2026, 12, 21);
            int isoYear = monday.get(IsoFields.WEEK_BASED_YEAR);
            int isoWeek = monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            List<Employee> employees = buildActiveEmployees();
            List<EmployeeAbsence> absences = List.of(absenceFor(ID_SARA,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), AbsenceType.MATERNITY));
            WeekResult result = engine.generate(isoYear, isoWeek, monday, employees, absences,
                    Set.of(LocalDate.of(2026, 12, 25), LocalDate.of(2026, 12, 26)), List.of());
            printResult(result, employees, monday);
        }

        System.out.println();

        // SCENARIO E — four workers on FERIAS simultaneously (F-shortage stress test)
        // Absent Mon-Fri: Andreia (F), Cristina (F), Nidia (F), Paulina (T)
        // Available on weekdays: Paula (F), Jéssica (F), Natty (T), Carolina (T), Crisanta (T)
        System.out.println("=".repeat(72));
        System.out.println("SCENARIO E — four workers on FERIAS Mon-Fri (Andreia+Cristina+Nidia+Paulina). ISO 2026-W24");
        System.out.println("=".repeat(72));
        {
            LocalDate monday = LocalDate.of(2026, 6, 8);
            List<Employee> employees = buildActiveEmployees();
            List<EmployeeAbsence> absences = List.of(
                    absenceFor(ID_SARA,     LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), AbsenceType.MATERNITY),
                    absenceFor(ID_ANDREIA,  monday, monday.plusDays(4), AbsenceType.FERIAS),
                    absenceFor(ID_CRISTINA, monday, monday.plusDays(4), AbsenceType.FERIAS),
                    absenceFor(ID_NIDIA,    monday, monday.plusDays(4), AbsenceType.FERIAS),
                    absenceFor(ID_PAULINA,  monday, monday.plusDays(4), AbsenceType.FERIAS)
            );
            WeekResult result = engine.generate(2026, 24, monday, employees, absences, Set.of(), List.of());
            printResult(result, employees, monday);
        }
    }

    // ---- builders -------------------------------------------------------

    private static List<Employee> buildActiveEmployees() {
        return List.of(
                emp(ID_PAULA,    "Paula",    Role.F),
                emp(ID_NIDIA,    "Nidia",    Role.F),
                emp(ID_JESSICA,  "Jéssica",  Role.F),
                emp(ID_ANDREIA,  "Andreia",  Role.F),
                emp(ID_CRISTINA, "Cristina", Role.F),
                emp(ID_NATTY,    "Natty",    Role.T),
                emp(ID_CAROLINA, "Carolina", Role.T),
                emp(ID_CRISANTA, "Crisanta", Role.T),
                emp(ID_PAULINA,  "Paulina",  Role.T)
        );
    }

    private static Employee emp(long id, String name, Role role) {
        Employee e = new Employee();
        e.setId(id);
        e.setName(name);
        e.setRole(role);
        e.setLastWeekendWorked(null);
        return e;
    }

    private static EmployeeAbsence absenceFor(long employeeId, LocalDate start, LocalDate end, AbsenceType type) {
        EmployeeAbsence a = new EmployeeAbsence();
        a.setEmployeeId(employeeId);
        a.setStartDate(start);
        a.setEndDate(end);
        a.setType(type);
        return a;
    }

    // ---- printing -------------------------------------------------------

    private static void printResult(WeekResult result, List<Employee> employees, LocalDate monday) {
        Map<Long, String> nameById = employees.stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getName));
        Map<Long, Role> roleById = employees.stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getRole));

        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (DayPlan day : result.getDays()) {
            String dayOfWeek = day.getDate().getDayOfWeek().name();
            String dateStr   = day.getDate().format(dateFmt);
            String typeName  = day.getDayType().name();
            System.out.printf("%s %s [%s]%n", dayOfWeek, dateStr, typeName);

            List<SlotAssignment> sorted = day.getAssignments().stream()
                    .sorted(Comparator.comparing(SlotAssignment::getStartTime)
                            .thenComparing(SlotAssignment::getEmployeeName))
                    .toList();

            for (SlotAssignment slot : sorted) {
                String name  = slot.getEmployeeName();
                String role  = "(" + slot.getEmployeeRole().name() + ")";
                String shift = String.format("%s-%s",
                        slot.getStartTime().toString(),
                        slot.getEndTime().toString());
                String breakStr = "";
                if (slot.getBreakStart() != null) {
                    breakStr = String.format(" [%s-%s]",
                            slot.getBreakStart(),
                            slot.getBreakEnd().toString());
                }
                double hours = slot.hoursWorked();
                System.out.printf("  %-12s %-4s %-18s%-12s %.1fh%n",
                        name, role, shift, breakStr, hours);
            }
            System.out.println();
        }

        // Weekly totals
        System.out.println("--- Weekly totals ---");
        Map<Long, Double> weeklyHours = result.getWeeklyHoursByEmployee();
        employees.stream()
                .sorted(Comparator.comparing(Employee::getName))
                .forEach(emp -> {
                    double h = weeklyHours.getOrDefault(emp.getId(), 0.0);
                    String status;
                    if (h > 40.0)      status = "[OVER]";
                    else if (h < ShiftTemplates.UNDERTIME_THRESHOLD_HOURS) status = "[WARN]";
                    else               status = "[OK]";
                    System.out.printf("  %s: %.1fh %s%n", emp.getName(), h, status);
                });

        // Validation messages
        if (!result.getValidationMessages().isEmpty()) {
            System.out.println();
            System.out.println("--- Validation messages ---");
            for (ValidationMessage msg : result.getValidationMessages()) {
                String datePart = msg.getDate() != null ? msg.getDate().format(dateFmt) : "—";
                String hourPart = msg.getHour() != null ? String.format("%02d:xx", msg.getHour()) : "——";
                System.out.printf("  [%s] %s %s  %s%n",
                        msg.getSeverity(), datePart, hourPart, msg.getMessage());
            }
        }
    }
}
