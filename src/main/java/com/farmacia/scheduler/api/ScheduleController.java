package com.farmacia.scheduler.api;

import com.farmacia.scheduler.api.dto.WeekResponse;
import com.farmacia.scheduler.api.dto.WeekWriteRequest;
import com.farmacia.scheduler.service.ScheduleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/schedules/weeks")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
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
}
