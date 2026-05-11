package com.farmacia.scheduler.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter @AllArgsConstructor
public class AssignmentResponse {
    private final Long id;
    private final EmployeeDto employee;
    private final String startTime;
    private final String endTime;
    private final String breakStart;
    private final String breakEnd;
    private final double hours;
}
