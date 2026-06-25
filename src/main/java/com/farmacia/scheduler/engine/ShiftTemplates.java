package com.farmacia.scheduler.engine;

import java.time.LocalTime;

public final class ShiftTemplates {

    private ShiftTemplates() {}

    // Weekday slots (Mon-Fri)
    // Slots 0-5 are essential (filled every day); slots 6-7 are optional overflow.
    public static final LocalTime[][] WEEKDAY_SLOTS = {
            { LocalTime.of(8,  0), LocalTime.of(16, 0), null,                null                }, // Slot 0: 08-16 essential morning
            { LocalTime.of(8,  0), LocalTime.of(16, 0), null,                null                }, // Slot 1: 08-16 essential morning (2nd)
            { LocalTime.of(9,  0), LocalTime.of(17, 0), null,                null                }, // Slot 2: 09-17 no break
            { LocalTime.of(10, 0), LocalTime.of(18, 0), null,                null                }, // Slot 3: 10-18 no break
            { LocalTime.of(14, 0), LocalTime.of(22, 0), null,                null                }, // Slot 4: 14-22 essential evening
            { LocalTime.of(14, 0), LocalTime.of(22, 0), null,                null                }, // Slot 5: 14-22 essential evening (2nd)
            { LocalTime.of(10, 0), LocalTime.of(19, 0), LocalTime.of(14, 0), LocalTime.of(15, 0) }, // Slot 6: 10-19 — fills on 7-person folga days, covers the 18-19 closing shoulder (break 14-15)
            { LocalTime.of(9,  0), LocalTime.of(18, 0), LocalTime.of(13, 0), LocalTime.of(14, 0) }, // Slot 7: 09-18 overflow, last to fill (break 13-14)
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
    // Floor is 37h, not 38h: a weekend week nets 37h (13h weekend pair + 3x8 weekday)
    // and 13 + 8n never lands in 38-40. Accepted by Martim 2026-06-25. See
    // decisions/014-undertime-floor-37h.md and REQUIREMENTS.md.
    public static final int OVERTIME_THRESHOLD_HOURS  = 40;
    public static final int UNDERTIME_THRESHOLD_HOURS = 37;
}