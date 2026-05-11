package com.farmacia.scheduler.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter @AllArgsConstructor
public class EmployeeDto {
    private final Long id;
    private final String name;
    private final String role;
}
