package com.farmacia.scheduler.service.exception;

import com.farmacia.scheduler.engine.model.ValidationMessage;

import java.util.List;

public class ScheduleHasValidationErrorsException extends RuntimeException {

    private final List<ValidationMessage> errors;

    public ScheduleHasValidationErrorsException(List<ValidationMessage> errors) {
        super("Schedule has %d validation error(s)".formatted(errors.size()));
        this.errors = List.copyOf(errors);
    }

    public List<ValidationMessage> getErrors() {
        return errors;
    }
}
