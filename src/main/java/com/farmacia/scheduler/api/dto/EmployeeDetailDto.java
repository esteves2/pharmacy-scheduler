package com.farmacia.scheduler.api.dto;

public record EmployeeDetailDto(
        Long id,
        String name,
        String role,
        String phone,
        String email,
        String notes,
        String birthday,
        int holidaysUsed,
        int holidaysRemaining,
        Integer contractHours) {}
