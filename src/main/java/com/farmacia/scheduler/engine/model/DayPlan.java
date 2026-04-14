package com.farmacia.scheduler.engine.model;

import com.farmacia.scheduler.model.Role;
import lombok.Getter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
public class DayPlan {

    private final LocalDate date;
    private final DayType dayType;
    private final List<SlotAssignment> assignments;

    public DayPlan(LocalDate date, DayType dayType) {
        this.date = date;
        this.dayType = dayType;
        this.assignments = new ArrayList<>();
    }

    public void addAssignment(SlotAssignment assignment) {
        assignments.add(assignment);
    }

    public void removeAssignment(SlotAssignment assignment) {
        assignments.remove(assignment);
    }

    public int headcountAtHour(int hour) {
        return (int) assignments.stream()
                .filter(slot -> slot.coversHour(hour))
                .count();
    }

    public long farmaceuticasAtHour(int hour) {
        return assignments.stream()
                .filter(slot -> slot.coversHour(hour))
                .filter(slot -> slot.getEmployeeRole() == Role.F)
                .count();
    }

    public boolean hasEmployee(long employeeId) {
        return assignments.stream()
                .anyMatch(slot -> slot.getEmployeeId() == employeeId);
    }
}