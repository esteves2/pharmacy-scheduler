package com.farmacia.scheduler.engine.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class ValidationMessage {

    private final Severity severity;
    private final LocalDate date;
    private final Integer hour;
    private final String message;

    public static ValidationMessage error(LocalDate date, Integer hour, String message) {
        return new ValidationMessage(Severity.ERROR, date, hour, message);
    }

    public static ValidationMessage warning(LocalDate date, Integer hour, String message) {
        return new ValidationMessage(Severity.WARNING, date, hour, message);
    }
}