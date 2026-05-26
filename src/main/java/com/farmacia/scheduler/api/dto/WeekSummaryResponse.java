package com.farmacia.scheduler.api.dto;

import java.time.LocalDate;

public record WeekSummaryResponse(
        int isoYear,
        int isoWeek,
        LocalDate weekStart,
        LocalDate weekEnd,
        String status) {}
