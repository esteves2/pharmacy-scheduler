package com.farmacia.scheduler.api.dto;

import java.time.LocalDate;

public record AssignmentWriteRequest(
        Long id,
        Long employeeId,
        LocalDate date,
        String startTime,
        String endTime,
        String breakStart,
        String breakEnd) {}
