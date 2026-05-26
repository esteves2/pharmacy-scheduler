package com.farmacia.scheduler.api.dto;

public record EmployeeSummaryResponse(
        EmployeeDto employee,
        double weeklyHours,
        double effectiveHours,
        String status) {}
