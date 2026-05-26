package com.farmacia.scheduler.api.dto;

import java.time.LocalDate;

public record HolidayResponse(Long id, LocalDate date, String name) {}
