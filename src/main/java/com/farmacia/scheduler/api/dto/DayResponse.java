package com.farmacia.scheduler.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDate;
import java.util.List;

@Getter @AllArgsConstructor
public class DayResponse {
    private final LocalDate date;
    private final String dayOfWeek;
    private final String dayType;
    private final List<AssignmentResponse> assignments;
}
