package com.farmacia.scheduler.engine.model;

import lombok.Getter;
import java.util.HashMap;
import java.util.Map;

@Getter
public class WeekAccumulator {

    private final Map<Long, Double> hoursByEmployee;
    private final Map<Long, Double> priorWeeksHoursByEmployee;

    public WeekAccumulator() {
        this.hoursByEmployee = new HashMap<>();
        this.priorWeeksHoursByEmployee = new HashMap<>();
    }

    public void seedPriorWeeksHours(long employeeId, double hours) {
        priorWeeksHoursByEmployee.put(employeeId, hours);
    }

    public void addHours(long employeeId, double hours) {
        hoursByEmployee.merge(employeeId, hours, Double::sum);
    }

    public void removeHours(long employeeId, double hours) {
        hoursByEmployee.merge(employeeId, -hours, Double::sum);
    }

    public double getWeeklyHours(long employeeId) {
        return hoursByEmployee.getOrDefault(employeeId, 0.0);
    }

    public double getPriorWeeksHours(long employeeId) {
        return priorWeeksHoursByEmployee.getOrDefault(employeeId, 0.0);
    }

    public Map<Long, Double> getAllWeeklyHours() {
        return Map.copyOf(hoursByEmployee);
    }
}