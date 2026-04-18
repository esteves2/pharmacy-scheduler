package com.farmacia.scheduler.engine;

import com.farmacia.scheduler.engine.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ScheduleValidator {

    private static final double WEEKLY_HOURS_UPPER = ShiftTemplates.OVERTIME_THRESHOLD_HOURS;
    private static final double WEEKLY_HOURS_LOWER = ShiftTemplates.UNDERTIME_THRESHOLD_HOURS;

    /**
     * Phase 4: Validate. Read-only scan of a completed week.
     * Per-hour checks: F coverage, minimum headcount, target headcount.
     * Per-employee checks: overtime, undertime.
     */
    public List<ValidationMessage> validate(List<DayPlan> days, WeekAccumulator accumulator, Map<Long, String> idToName) {
        List<ValidationMessage> messages = new ArrayList<>();

        for (DayPlan day : days) {
            validateDay(day, messages);
        }

        for (Map.Entry<Long, Double> entry : accumulator.getAllWeeklyHours().entrySet()) {
            long employeeId = entry.getKey();
            double hours = entry.getValue();
            String name = idToName.getOrDefault(employeeId, "Employee " + employeeId);

            if (hours > WEEKLY_HOURS_UPPER) {
                messages.add(ValidationMessage.error(null, null,
                        name + " exceeds 40h (" + hours + "h)"));
            } else if (hours < WEEKLY_HOURS_LOWER && hours > 0) {
                messages.add(ValidationMessage.warning(null, null,
                        name + " below 25h (" + hours + "h)"));
            }
        }

        return messages;
    }

    private void validateDay(DayPlan day, List<ValidationMessage> messages) {
        int openHour = openHour(day.getDayType());
        int closeHour = closeHour(day.getDayType());

        for (int hour = openHour; hour < closeHour; hour++) {
            int headcount = day.headcountAtHour(hour);
            long farmaceuticas = day.farmaceuticasAtHour(hour);
            int minimum = minimumHeadcount(day.getDayType(), hour);
            int target = targetHeadcount(day.getDayType(), hour);

            if (farmaceuticas == 0) {
                messages.add(ValidationMessage.error(day.getDate(), hour,
                        "No farmacêutica present"));
            }
            if (headcount < minimum) {
                messages.add(ValidationMessage.error(day.getDate(), hour,
                        "Headcount below minimum (" + headcount + " < " + minimum + ")"));
            } else if (headcount < target) {
                messages.add(ValidationMessage.warning(day.getDate(), hour,
                        "Headcount below target (" + headcount + " < " + target + ")"));
            }
        }
    }

    private int openHour(DayType dayType) {
        return 8;
    }

    private int closeHour(DayType dayType) {
        return switch (dayType) {
            case WEEKDAY, SATURDAY -> 22;
            case SUNDAY, HOLIDAY -> 20;
        };
    }

    private int minimumHeadcount(DayType dayType, int hour) {
        if (dayType == DayType.SUNDAY || dayType == DayType.HOLIDAY || dayType == DayType.SATURDAY) {
            return 2;
        }
        // WEEKDAY
        if (hour < 10) return 2;
        if (hour < 19) return 3;
        return 2;
    }

    private int targetHeadcount(DayType dayType, int hour) {
        if (dayType == DayType.SUNDAY || dayType == DayType.HOLIDAY || dayType == DayType.SATURDAY) {
            return 2;
        }
        // WEEKDAY
        if (hour < 9) return 2;
        if (hour < 10) return 3;
        if (hour < 19) return 4;
        if (hour < 21) return 3;
        return 2;
    }
}