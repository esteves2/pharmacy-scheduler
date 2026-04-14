package com.farmacia.scheduler.engine.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class WeekResult {

    private final int isoYear;
    private final int isoWeek;
    private final List<DayPlan> days;
    private final List<ValidationMessage> validationMessages;
    private final Map<Long, Double> weeklyHoursByEmployee;

    public boolean hasErrors() {
        return validationMessages.stream()
                .anyMatch(msg -> msg.getSeverity() == Severity.ERROR);
    }

    public List<ValidationMessage> getErrors() {
        return validationMessages.stream()
                .filter(msg -> msg.getSeverity() == Severity.ERROR)
                .toList();
    }

    public List<ValidationMessage> getWarnings() {
        return validationMessages.stream()
                .filter(msg -> msg.getSeverity() == Severity.WARNING)
                .toList();
    }
}