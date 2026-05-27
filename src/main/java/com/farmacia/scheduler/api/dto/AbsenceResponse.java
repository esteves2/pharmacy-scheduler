package com.farmacia.scheduler.api.dto;

import java.time.LocalDate;

public record AbsenceResponse(
        Long id,
        EmployeeDto employee,
        LocalDate startDate,
        LocalDate endDate,
        String type,
        String note) {}
