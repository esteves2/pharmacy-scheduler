package com.farmacia.scheduler.api.dto;

import java.util.List;

public record WeekResponse(
        int isoYear,
        int isoWeek,
        String status,
        List<DayResponse> days,
        List<EmployeeSummaryResponse> employeeSummaries,
        List<ValidationMessageResponse> validationMessages) {}
