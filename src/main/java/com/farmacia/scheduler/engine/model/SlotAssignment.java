package com.farmacia.scheduler.engine.model;

import com.farmacia.scheduler.model.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@AllArgsConstructor
public class SlotAssignment {

    private final long employeeId;
    private final String employeeName;
    private final Role employeeRole;
    private final LocalDate date;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final LocalTime breakStart;
    private final LocalTime breakEnd;

    public double hoursWorked() {
        long totalMinutes = Duration.between(startTime, endTime).toMinutes();
        if (breakStart != null && breakEnd != null) {
            totalMinutes -= Duration.between(breakStart, breakEnd).toMinutes();
        }
        return totalMinutes / 60.0;
    }

    public boolean coversHour(int hour) {
        if (hour < startTime.getHour() || hour >= endTime.getHour()) {
            return false;
        }

        return breakStart == null || hour < breakStart.getHour() || hour >= breakEnd.getHour();
    }
}