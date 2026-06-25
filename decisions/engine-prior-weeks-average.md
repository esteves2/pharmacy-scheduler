# Decision: seed prior weeks as average hours/week, not raw sum

## What changed
`seedPriorWeeks` in `ScheduleEngine` now divides each employee's total prior hours by the number of distinct ISO weeks present in `priorAssignments` before seeding `WeekAccumulator.priorWeeksHours`.

## Why
The primary sort key in `WeekdayFiller` is `getPriorWeeksHours`. When seeded as a raw 4-week sum, an employee who worked a weekend gets fewer weekday hours that week, drops their cumulative total, and is picked first the next week — which gives them more hours, raising their sum, which pushes them to the back, and so on. This oscillation worsens across weeks.

Storing the **average hours per week** makes the sort key scale-invariant: it doesn't matter whether the lookback window has 1 week of history or 4. The comparison between employees stays meaningful as data accumulates.

## Approved by
Martim (confirmed direction: "doing the average is probably better").
