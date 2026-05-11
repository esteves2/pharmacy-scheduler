package com.farmacia.scheduler.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter @AllArgsConstructor
public class WeekResponse {
    private final int isoYear;
    private final int isoWeek;
    private final String status;
    private final List<DayResponse> days;
    private final List<EmployeeSummaryResponse> employeeSummaries;
    private final List<ValidationMessageResponse> validationMessages;
}
