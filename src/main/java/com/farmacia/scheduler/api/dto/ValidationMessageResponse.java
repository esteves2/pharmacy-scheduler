package com.farmacia.scheduler.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDate;

@Getter @AllArgsConstructor
public class ValidationMessageResponse {
    private final String severity;
    private final LocalDate date;
    private final Integer hour;
    private final String message;
}
