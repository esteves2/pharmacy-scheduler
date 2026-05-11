package com.farmacia.scheduler.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter @AllArgsConstructor
public class EmployeeSummaryResponse {
    private final EmployeeDto employee;
    private final double weeklyHours;
    private final String status;
}
