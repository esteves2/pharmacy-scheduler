package com.farmacia.scheduler.engine;

import java.time.LocalTime;

public final class ShiftTemplates {

    private ShiftTemplates() {}

    // Weekday slots (Mon-Fri)
    public static final LocalTime[][] WEEKDAY_SLOTS = {
            { LocalTime.of(8, 0),  LocalTime.of(15, 0), null,                null },                // Slot 1: 7h
            { LocalTime.of(8, 0),  LocalTime.of(15, 0), null,                null },                // Slot 2: 7h
            { LocalTime.of(9, 0),  LocalTime.of(18, 0), LocalTime.of(13, 0), LocalTime.of(14, 0) }, // Slot 3: 8h
            { LocalTime.of(10, 0), LocalTime.of(19, 0), LocalTime.of(14, 0), LocalTime.of(15, 0) }, // Slot 4: 8h
            { LocalTime.of(14, 0), LocalTime.of(22, 0), null,                null },                // Slot 5: 8h
            { LocalTime.of(14, 0), LocalTime.of(22, 0), null,                null },                // Slot 6: 8h
    };

    // Saturday shifts
    public static final LocalTime SAT_MORNING_START = LocalTime.of(8, 0);
    public static final LocalTime SAT_MORNING_END   = LocalTime.of(15, 0);
    public static final LocalTime SAT_EVENING_START  = LocalTime.of(15, 0);
    public static final LocalTime SAT_EVENING_END    = LocalTime.of(22, 0);

    // Sunday / Holiday shifts
    public static final LocalTime SUN_MORNING_START = LocalTime.of(8, 0);
    public static final LocalTime SUN_MORNING_END   = LocalTime.of(14, 0);
    public static final LocalTime SUN_EVENING_START  = LocalTime.of(14, 0);
    public static final LocalTime SUN_EVENING_END    = LocalTime.of(20, 0);

    // Headcount rules - weekday
    public static final int WEEKDAY_OPEN_HOUR  = 8;
    public static final int WEEKDAY_CLOSE_HOUR = 22;

    // Headcount rules - weekend/holiday
    public static final int WEEKEND_OPEN_HOUR  = 8;
    public static final int WEEKEND_CLOSE_HOUR_SAT = 22;
    public static final int WEEKEND_CLOSE_HOUR_SUN = 20;

    public static final int WEEKEND_TARGET = 2;
    public static final int WEEKEND_MINIMUM = 2;
    public static final int WEEKEND_WORKERS = 4;
    public static final int WEEKEND_PAIRS = 2;
    public static final int REQUIRED_FARMACEUTICAS_PER_PAIR = 1;

    // Weekly-hour thresholds
    public static final int OVERTIME_THRESHOLD_HOURS  = 40;
    public static final int UNDERTIME_THRESHOLD_HOURS = 25;
}