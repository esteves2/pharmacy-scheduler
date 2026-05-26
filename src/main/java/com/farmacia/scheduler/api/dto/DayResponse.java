package com.farmacia.scheduler.api.dto;

import java.time.LocalDate;
import java.util.List;

public record DayResponse(
        LocalDate date,
        String dayOfWeek,
        String dayType,
        List<AssignmentResponse> assignments) {}
