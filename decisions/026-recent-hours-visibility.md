# 026 — Recent-hours visibility per employee

**Date:** 2026-06-25
**Approved by:** Martim (feature #4)
**Files:** `api/EmployeeController.java`, `api/dto/EmployeeDetailDto.java`,
`frontend/src/api/types.ts`, `frontend/src/pages/EmployeesPage.tsx`

## Why

Cross-week hour balancing is a soft tiebreaker, and the UI only showed per-week totals, so
a manager couldn't see if someone was drifting low over a month. This adds a rolling
average so drift is visible — without trying to force a monthly 40h target (unreachable for
weekend-rotating staff, who cap around 38.5h).

## What

`EmployeeController.computeRecentWeeklyAverage()` sums each employee's worked hours from
`shift_assignment` over the last 4 weeks (ending yesterday) and divides by 4, rounded to one
decimal. Hours per shift are computed from start/end minus any lunch break. Exposed as
`recentWeeklyAverage` on `EmployeeDetailDto`.

The Funcionários page shows it two ways: a compact `Xh/sem` on each list row, and a "Horas
recentes" line in the edit panel. Both flag **red when the average is >0 and below 37h/week**
("Abaixo de 37h/semana — a acompanhar"), which catches genuine under-allocation while
ignoring the 37-40h band and the no-history/bootstrap case (0h shows neutral).

## Notes

- Derived from history each request (no new table, no stored field) — same principle as the
  weekend/holiday scoreboards.
- Threshold 37h is the accepted floor; weekend-rotating staff average ~38.5h so anything
  clearly under 37 is worth a look.
- The `update` endpoint ignores the incoming `recentWeeklyAverage` (it's computed, read-only).
- Frontend must be rebuilt for the display to appear (built `static/` is gitignored).
