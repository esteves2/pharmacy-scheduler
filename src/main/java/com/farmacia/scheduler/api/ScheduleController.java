package com.farmacia.scheduler.api;

import com.farmacia.scheduler.api.dto.WeekResponse;
import com.farmacia.scheduler.api.dto.WeekSummaryResponse;
import com.farmacia.scheduler.api.dto.WeekWriteRequest;
import com.farmacia.scheduler.repository.ScheduleWeekRepository;
import com.farmacia.scheduler.service.ScheduleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/schedules/weeks")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ScheduleWeekRepository scheduleWeekRepository;

    public ScheduleController(ScheduleService scheduleService, ScheduleWeekRepository scheduleWeekRepository) {
        this.scheduleService = scheduleService;
        this.scheduleWeekRepository = scheduleWeekRepository;
    }

    @GetMapping
    public List<WeekSummaryResponse> listByMonth(
            @RequestParam int year,
            @RequestParam int month) {
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());
        LocalDate cursor = firstDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        List<WeekSummaryResponse> result = new ArrayList<>();
        while (!cursor.isAfter(lastDay)) {
            int isoYear = cursor.get(WeekFields.ISO.weekBasedYear());
            int isoWeek = cursor.get(WeekFields.ISO.weekOfWeekBasedYear());
            String status = scheduleWeekRepository
                    .findByIsoYearAndIsoWeek(isoYear, isoWeek)
                    .map(w -> w.getStatus().name())
                    .orElse(null);
            result.add(new WeekSummaryResponse(isoYear, isoWeek, cursor, cursor.plusDays(6), status));
            cursor = cursor.plusWeeks(1);
        }
        return result;
    }

    @PostMapping("/{isoYear}/{isoWeek}/generate")
    public ResponseEntity<WeekResponse> generate(
            @PathVariable int isoYear,
            @PathVariable int isoWeek) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.generate(isoYear, isoWeek));
    }

    @GetMapping("/{isoYear}/{isoWeek}")
    public ResponseEntity<WeekResponse> getWeek(
            @PathVariable int isoYear,
            @PathVariable int isoWeek) {
        return ResponseEntity.ok(scheduleService.getWeek(isoYear, isoWeek));
    }

    @PutMapping("/{isoYear}/{isoWeek}")
    public ResponseEntity<WeekResponse> save(
            @PathVariable int isoYear,
            @PathVariable int isoWeek,
            @RequestBody WeekWriteRequest request) {
        return ResponseEntity.ok(scheduleService.save(isoYear, isoWeek, request));
    }

    @PostMapping("/{isoYear}/{isoWeek}/publish")
    public ResponseEntity<WeekResponse> publish(
            @PathVariable int isoYear,
            @PathVariable int isoWeek) {
        return ResponseEntity.ok(scheduleService.publish(isoYear, isoWeek));
    }

    @PostMapping("/{isoYear}/{isoWeek}/regenerate")
    public ResponseEntity<WeekResponse> regenerate(
            @PathVariable int isoYear,
            @PathVariable int isoWeek) {
        return ResponseEntity.ok(scheduleService.regenerate(isoYear, isoWeek));
    }

    @PostMapping("/{isoYear}/{isoWeek}/replan")
    public ResponseEntity<WeekResponse> replan(
            @PathVariable int isoYear,
            @PathVariable int isoWeek) {
        return ResponseEntity.ok(scheduleService.replan(isoYear, isoWeek));
    }

    @DeleteMapping("/{isoYear}/{isoWeek}")
    public ResponseEntity<Void> delete(
            @PathVariable int isoYear,
            @PathVariable int isoWeek) {
        scheduleService.delete(isoYear, isoWeek);
        return ResponseEntity.noContent().build();
    }
}
