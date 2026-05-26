package com.farmacia.scheduler.api.dto;

import java.time.LocalDate;

public record ValidationMessageResponse(String severity, LocalDate date, Integer hour, String message) {}
