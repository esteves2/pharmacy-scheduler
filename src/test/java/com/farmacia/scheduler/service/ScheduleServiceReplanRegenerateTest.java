package com.farmacia.scheduler.service;

import com.farmacia.scheduler.api.dto.WeekResponse;
import com.farmacia.scheduler.model.ScheduleWeek;
import com.farmacia.scheduler.model.ShiftAssignment;
import com.farmacia.scheduler.model.WeekStatus;
import com.farmacia.scheduler.repository.ScheduleWeekRepository;
import com.farmacia.scheduler.repository.ShiftAssignmentRepository;
import com.farmacia.scheduler.service.exception.ScheduleAlreadyPublishedException;
import com.farmacia.scheduler.service.exception.ScheduleNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ScheduleServiceReplanRegenerateTest {

    @DynamicPropertySource
    static void isolatedSqlite(DynamicPropertyRegistry registry) throws IOException {
        Path tempDir = Files.createTempDirectory("pharmacy-test-replan-");
        String url = "jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath();
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "1");
    }

    @Autowired ScheduleService scheduleService;
    @Autowired ScheduleWeekRepository scheduleWeekRepository;
    @Autowired ShiftAssignmentRepository shiftAssignmentRepository;

    // --- regenerate ---

    @Test
    void regenerate_wipesAndRegeneratesAssignments() {
        scheduleService.generate(2027, 10);
        List<ShiftAssignment> before = assignmentsFor(2027, 10);
        assertThat(before).isNotEmpty();

        scheduleService.regenerate(2027, 10);
        List<ShiftAssignment> after = assignmentsFor(2027, 10);

        assertThat(after).isNotEmpty();
        assertThat(weekStatus(2027, 10)).isEqualTo(WeekStatus.DRAFT);
        // IDs must differ — old rows were deleted and new ones inserted
        assertThat(after.stream().map(ShiftAssignment::getId))
                .doesNotContainAnyElementsOf(before.stream().map(ShiftAssignment::getId).toList());
    }

    @Test
    void regenerate_blockedOnPublishedWeek() {
        scheduleService.generate(2027, 11);
        scheduleService.publish(2027, 11);

        assertThatThrownBy(() -> scheduleService.regenerate(2027, 11))
                .isInstanceOf(ScheduleAlreadyPublishedException.class);

        // Week must remain PUBLISHED
        assertThat(weekStatus(2027, 11)).isEqualTo(WeekStatus.PUBLISHED);
    }

    @Test
    void regenerate_throwsWhenWeekNotFound() {
        assertThatThrownBy(() -> scheduleService.regenerate(2027, 52))
                .isInstanceOf(ScheduleNotFoundException.class);
    }

    // --- replan ---

    @Test
    void replan_throwsWhenWeekNotFound() {
        assertThatThrownBy(() -> scheduleService.replan(2027, 53))
                .isInstanceOf(ScheduleNotFoundException.class);
    }

    @Test
    void replan_onPastWeek_returnsCurrentStateUnchanged() {
        // ISO week 1 of 2020 (Mon 2019-12-30) is well in the past.
        // today > weekEnd so replan short-circuits and returns the current state.
        scheduleService.generate(2020, 1);
        List<ShiftAssignment> before = assignmentsFor(2020, 1);

        WeekResponse response = scheduleService.replan(2020, 1);

        List<ShiftAssignment> after = assignmentsFor(2020, 1);
        assertThat(after).hasSameSizeAs(before);
        assertThat(after.stream().map(ShiftAssignment::getId))
                .containsExactlyInAnyOrderElementsOf(before.stream().map(ShiftAssignment::getId).toList());
        // Status unchanged — week was DRAFT, replan on past week doesn't touch it
        assertThat(weekStatus(2020, 1)).isEqualTo(WeekStatus.DRAFT);
    }

    @Test
    void replan_onFutureWeek_regeneratesAllAssignments() {
        // ISO week 1 of 2030 (Mon 2029-12-30) is well in the future.
        // today < monday so replanFrom = monday, lockedAssignments = empty → full regeneration.
        scheduleService.generate(2030, 1);
        List<ShiftAssignment> before = assignmentsFor(2030, 1);

        scheduleService.replan(2030, 1);
        List<ShiftAssignment> after = assignmentsFor(2030, 1);

        assertThat(after).isNotEmpty();
        assertThat(weekStatus(2030, 1)).isEqualTo(WeekStatus.DRAFT);
        // Old rows were deleted, new ones inserted
        assertThat(after.stream().map(ShiftAssignment::getId))
                .doesNotContainAnyElementsOf(before.stream().map(ShiftAssignment::getId).toList());
    }

    @Test
    void replan_dropsPublishedWeekBackToDraft() {
        scheduleService.generate(2027, 20);
        scheduleService.publish(2027, 20);
        assertThat(weekStatus(2027, 20)).isEqualTo(WeekStatus.PUBLISHED);

        // Week 20 of 2027 starts Mon 2027-05-17 — well in the future from any run date,
        // so replan regenerates everything and drops status back to DRAFT.
        scheduleService.replan(2027, 20);

        assertThat(weekStatus(2027, 20)).isEqualTo(WeekStatus.DRAFT);
    }

    // --- helpers ---

    private List<ShiftAssignment> assignmentsFor(int isoYear, int isoWeek) {
        ScheduleWeek week = scheduleWeekRepository
                .findByIsoYearAndIsoWeek(isoYear, isoWeek)
                .orElseThrow();
        return shiftAssignmentRepository.findByScheduleWeekId(week.getId());
    }

    private WeekStatus weekStatus(int isoYear, int isoWeek) {
        return scheduleWeekRepository
                .findByIsoYearAndIsoWeek(isoYear, isoWeek)
                .orElseThrow()
                .getStatus();
    }
}
