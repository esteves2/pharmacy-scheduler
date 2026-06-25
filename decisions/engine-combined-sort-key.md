# Decision: sort by priorWeeksHours + weeklyHours as single key

## What changed
`WeekdayFiller.pickEmployee` previously sorted by `priorWeeksHours` (primary) then `weeklyHours` (secondary). Now it sorts by `priorWeeksHours + weeklyHours` as a single combined key.

## Why
With a two-key sort, `priorWeeksHours` is static for the entire week. Whoever has the lowest prior average wins every single slot all week — they accumulate 40–45h while others get 6h. Next week the positions flip. The oscillation persists even after switching to average-based seeding.

With a combined key, an employee's growing current-week hours are continuously added to their prior average. Once their running total catches up to a colleague's, the colleague starts getting picked instead. This distributes hours evenly *within* the week, not just across weeks.

## Approved by
Martim (confirmed oscillation still present after average-only fix, wanted better distribution).
