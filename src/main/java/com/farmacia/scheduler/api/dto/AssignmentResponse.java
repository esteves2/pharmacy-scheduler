package com.farmacia.scheduler.api.dto;

public record AssignmentResponse(
        Long id,
        EmployeeDto employee,
        String startTime,
        String endTime,
        String breakStart,
        String breakEnd,
        double hours) {}
