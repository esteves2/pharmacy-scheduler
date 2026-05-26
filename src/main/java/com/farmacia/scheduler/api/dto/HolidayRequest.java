package com.farmacia.scheduler.api.dto;

import java.time.LocalDate;

public record HolidayRequest(LocalDate date, String name) {}
