package com.farmacia.scheduler.service.exception;

public class ScheduleAlreadyPublishedException extends RuntimeException {

    public ScheduleAlreadyPublishedException() {
        super();
    }

    public ScheduleAlreadyPublishedException(String message) {
        super(message);
    }
}
