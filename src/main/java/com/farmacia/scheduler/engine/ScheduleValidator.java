package com.farmacia.scheduler.engine;

import com.farmacia.scheduler.engine.model.*;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ScheduleValidator {

    private static final double WEEKLY_HOURS_UPPER = ShiftTemplates.OVERTIME_THRESHOLD_HOURS;
    private static final double WEEKLY_HOURS_LOWER = ShiftTemplates.UNDERTIME_THRESHOLD_HOURS;

    public List<ValidationMessage> validate(
            List<DayPlan> days,
            WeekAccumulator accumulator,
            Map<Long, String> idToName,
            Map<Long, Double> absenceCredits) {

        List<ValidationMessage> messages = new ArrayList<>();

        for (DayPlan day : days) {
            validateDay(day, messages);
        }

        for (Map.Entry<Long, Double> entry : accumulator.getAllWeeklyHours().entrySet()) {
            long employeeId = entry.getKey();
            double worked = entry.getValue();
            double effective = worked + absenceCredits.getOrDefault(employeeId, 0.0);
            String name = idToName.getOrDefault(employeeId, "Employee " + employeeId);

            if (worked > WEEKLY_HOURS_UPPER) {
                messages.add(ValidationMessage.error(null, null,
                        name + " exceeds " + (int) WEEKLY_HOURS_UPPER + "h (" + worked + "h)"));
            } else if (effective < WEEKLY_HOURS_LOWER) {
                messages.add(ValidationMessage.warning(null, null,
                        name + " below " + (int) WEEKLY_HOURS_LOWER + "h (" + effective + "h effective)"));
            }
        }

        return consolidate(messages);
    }

    /**
     * Collapses duplicate messages (same severity + text) into a single entry.
     * Repeated issues across multiple days are noted once with a count and date list.
     */
    private List<ValidationMessage> consolidate(List<ValidationMessage> raw) {
        // Preserve insertion order; key = severity + message text
        Map<String, List<ValidationMessage>> groups = new LinkedHashMap<>();
        for (ValidationMessage m : raw) {
            String key = m.getSeverity() + "|" + m.getMessage();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        }

        List<ValidationMessage> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d/MM");

        for (List<ValidationMessage> group : groups.values()) {
            if (group.size() == 1) {
                result.add(group.get(0));
            } else {
                ValidationMessage first = group.get(0);
                List<String> dates = group.stream()
                        .filter(m -> m.getDate() != null)
                        .map(m -> m.getDate().format(fmt))
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());
                String suffix = dates.isEmpty()
                        ? " [×" + group.size() + "]"
                        : " [×" + group.size() + " — " + String.join(", ", dates) + "]";
                result.add(new ValidationMessage(first.getSeverity(), null, null, first.getMessage() + suffix));
            }
        }
        return result;
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
                messages.add(ValidationMessage.info(day.getDate(), hour,
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