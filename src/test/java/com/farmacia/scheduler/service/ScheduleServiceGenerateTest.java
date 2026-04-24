package com.farmacia.scheduler.service;

import com.farmacia.scheduler.engine.model.WeekResult;
import com.farmacia.scheduler.model.Employee;
import com.farmacia.scheduler.model.ScheduleWeek;
import com.farmacia.scheduler.model.ShiftAssignment;
import com.farmacia.scheduler.model.WeekStatus;
import com.farmacia.scheduler.repository.EmployeeRepository;
import com.farmacia.scheduler.repository.ScheduleWeekRepository;
import com.farmacia.scheduler.repository.ShiftAssignmentRepository;
import com.farmacia.scheduler.service.exception.ScheduleAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Week 10 of 2026: March 2 (Mon) – March 8 (Sun). No public holidays. Sara (id=6) on maternity
// the whole year, leaving 8 active employees — clean inputs for the engine.
@SpringBootTest
class ScheduleServiceGenerateTest {

    private static final int ISO_YEAR   = 2026;
    private static final int ISO_WEEK   = 10;
    private static final LocalDate WEEK_START = LocalDate.of(2026, 3, 2);
    private static final LocalDate WEEK_END   = LocalDate.of(2026, 3, 8);

    @DynamicPropertySource
    static void isolatedSqlite(DynamicPropertyRegistry registry) throws IOException {
        Path tempDir = Files.createTempDirectory("pharmacy-test-");
        String url = "jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath();
        registry.add("spring.datasource.url", () -> url);
        // SQLite does not support concurrent writers; one connection avoids lock contention
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "1");
    }

    @Autowired ScheduleService scheduleService;
    @Autowired ScheduleWeekRepository scheduleWeekRepository;
    @Autowired ShiftAssignmentRepository shiftAssignmentRepository;
    @Autowired EmployeeRepository employeeRepository;

    @Test
    void generate_writesDraftWeekWithAssignments() {
        WeekResult result = scheduleService.generate(ISO_YEAR, ISO_WEEK);

        // --- WeekResult structure ---
        assertThat(result.getDays()).hasSize(7);
        assertThat(result.getDays()).allSatisfy(day ->
                assertThat(day.getAssignments()).isNotEmpty());
        assertThat(result.getErrors()).isEmpty();

        // --- Persisted ScheduleWeek ---
        ScheduleWeek week = scheduleWeekRepository
                .findByIsoYearAndIsoWeek(ISO_YEAR, ISO_WEEK)
                .orElseThrow();
        assertThat(week.getStatus()).isEqualTo(WeekStatus.DRAFT);
        assertThat(week.getGeneratedAt()).isNotNull();

        // --- Persisted ShiftAssignments ---
        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByScheduleWeekId(week.getId());
        // 8 employees × 7 days not all work every day; weekend has 4 slots, weekdays ~6/day
        assertThat(assignments).hasSizeBetween(20, 50);

        Set<Long> knownEmployeeIds = employeeRepository.findAll().stream()
                .map(Employee::getId)
                .collect(Collectors.toSet());

        for (ShiftAssignment a : assignments) {
            assertThat(knownEmployeeIds).contains(a.getEmployeeId());
            assertThat(a.getDate()).isBetween(WEEK_START, WEEK_END);
            assertThat(a.getStartTime()).isNotBlank();
            assertThat(a.getEndTime()).isNotBlank();
        }

        // Weekend shift templates have no break (ShiftTemplates Sat/Sun slots)
        assertThat(assignments).anySatisfy(a -> assertThat(a.getBreakStart()).isNull());

        // --- Idempotency guard: second call must throw ---
        // The first generate() committed its transaction, so the ScheduleWeek row is visible here.
        assertThatThrownBy(() -> scheduleService.generate(ISO_YEAR, ISO_WEEK))
                .isInstanceOf(ScheduleAlreadyExistsException.class);
    }
}
