# 027 — Remove recent-hours visibility; colour lunch breaks distinctly

**Date:** 2026-07-09
**Approved by:** Martim
**Files:** `api/EmployeeController.java`, `api/dto/EmployeeDetailDto.java`,
`frontend/src/api/types.ts`, `frontend/src/pages/EmployeesPage.tsx`,
`frontend/src/components/schedule/WeekView.tsx`. Deletes `decisions/026`.

## Remove the recent-hours average (reverts 026)

Feature #4 showed each employee a rolling 4-week average (`Xh/sem`) on the
Funcionários page, flagged red below 37h. Removed at Martim's call: it serves no
purpose. The engine already enforces 37–40h every week, so the average mostly
restates a guarantee already in place. The only sub-37 rows it surfaced were
explained by data already on screen — férias (Andreia), no history (Sara), or a
stale draft (Jéssica 41h) — none of them a decision a manager would act on. It
also read as buggy: 36.5h rounded to "37h" but flagged red, so two identical-looking
rows had different colours.

Reverted `EmployeeController` (dropped `ShiftAssignmentRepository`,
`computeRecentWeeklyAverage`, `hoursOf`), the `recentWeeklyAverage` field on
`EmployeeDetailDto` and `types.ts`, and all display on `EmployeesPage`. Decision
026 is deleted — the feature never shipped (was never committed).

## Colour lunch breaks distinctly

The schedule cell stacked three lines — shift span, lunch break, total hours —
with the break and the total both in `text-gray-400`, so the break was
indistinguishable from the total. The break now renders as an amber pill
(`text-amber-700 bg-amber-50`), separating it from the shift span (dark, bold) and
the total (gray). Amber was chosen because green/blue/purple/orange already carry
meaning in this view (Sat/Sun/holiday backgrounds, absence styles); amber was
unused and reads as a soft "pause".

## Not changed

Engine, break-budget logic, and the AssignmentModal are untouched — this is a
display-only change plus a feature removal.
