package com.farmacia.scheduler.api.dto;

import java.time.LocalDate;

public record AbsenceRequest(
        Long employeeId,
        LocalDate startDate,
        LocalDate endDate,
        String type,
        String note) {}
