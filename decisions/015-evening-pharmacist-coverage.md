# 015 — Guarantee a pharmacist in the weekday evening

**Date:** 2026-06-25
**Approved by:** Martim (go-ahead to fix bug #2)
**File:** `engine/WeekdayFiller.java`

## Problem

W26 had 17 weekday hours with no F present (`Nenhuma farmacêutica presente ×17`).
The closing block 19:00–22:00 is covered only by the two evening slots (14–22). Both
went to técnicas all week, so after the last day-shift F left (~18:00) the pharmacy ran
technician-only until close. Illegal and blocks publishing.

## Root cause

The old `pickEmployee` decided F-need with `needsFarmaceuticaNow`, which checked only
whether a *later slot's time* overlapped an uncovered hour — not whether that slot would
actually hold an F. So the day slots (0–3) consumed every available F, and by the time
slot 5 detected it truly needed one for 19–22, none was left. Same-slot-all-week then
froze the technicians-only evening across Mon–Fri.

## Fix

Replaced the two role-blind helpers (`needsFarmaceuticaNow`,
`mustReserveFarmaceuticaForLaterSlots`) with a coverage invariant:

> A morning F (slot 0/1, 08–16) covers hours 8–15; an evening F (slot 4/5, 14–22) covers
> 14–21. Together they cover every open hour. So guarantee **one F in {0,1}** and **one F
> in {4,5}**, and reserve an F for the evening so the day slots cannot drain the pool.

Logic in `pickEmployee`:
- `reserve` = 1 while the evening group is still unmet and ahead, so day slots (0–3) hand
  off to a técnica rather than spend the last F.
- A mandatory slot takes an F when it is the group's last chance, or when doing so still
  leaves enough F's for the other mandatory group.
- A non-mandatory / already-satisfied slot refuses an F if `fAvailable <= reserve`.

## Scope / limits

- Per-day guarantee: if any F is available for the evening that day, one is assigned.
  W26 has ≥3 F's available every weekday, so it should drop to 0 F-coverage errors.
- Residual edge (not addressed): if the evening slot's same-slot owner is on folga and
  every other F that day is already a day-slot owner, the evening can still flag. Relies
  on a substitute F being available — true for W26. A later refinement could prefer a
  non-weekend-worker F as the evening owner for week-long stability.
- Degenerate 1-F day: only one group can be covered; the other flags. Unavoidable (one
  person cannot staff 08–22). Realistic days have ≥2 F's.
- Does NOT touch bug #3 (Andreia 45h / weekend-worker hours). W27 will still show that
  error until #3 is done.

## Removed

`needsFarmaceuticaNow` and `mustReserveFarmaceuticaForLaterSlots` deleted. The
`remainingSlots`, `start`, `end`, `breakEnd` arguments to `pickEmployee` removed (only
those helpers used them).

## How to verify

Regenerate W26 (2026). Expect `Nenhuma farmacêutica presente` to disappear (0 errors of
that type). Every weekday evening should now show at least one F (column role F) working
14–22. W27's Andreia 45h error is expected to remain.
