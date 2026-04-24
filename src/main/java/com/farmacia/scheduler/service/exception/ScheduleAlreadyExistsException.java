package com.farmacia.scheduler.service.exception;

public class ScheduleAlreadyExistsException extends RuntimeException {

    public ScheduleAlreadyExistsException(String message) {
        super(message);
    }
}
