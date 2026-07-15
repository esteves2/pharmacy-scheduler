# 025 — Finish replan: folgas + coverage anchoring on regenerated days

**Date:** 2026-06-25
**Approved by:** Martim
**File:** `engine/ScheduleEngine.java` (`replan`), test in `ScheduleEngineTest`

## Problem

`replan` (mid-week regeneration, e.g. a sick day) re-ran the engine from `today` forward
but skipped two things `generate` does: it never computed programmatic folgas, and it
passed an empty weekend-worker set to the filler. So a weekend worker in the regenerated
portion could work every future weekday plus the weekend — only the overtime trimmer kept
them under 40h, and the F-anchor preference (a non-weekend pharmacist on the evening) was
off. Coverage held via the filler's F-reservation, but the result was rougher than a fresh
generate.

## Fix

After the weekend/holiday phases, `replan` now:

1. Derives `weekendWorkerIds` from whoever ended up on Sat/Sun in `daysByDate` (the locked
   past crew or a newly-assigned one).
2. Runs `computeFolgaDays(folgaWorkers, monday, holidays)` — the same deterministic folga
   logic (holiday-aware, decision 024).
3. In the weekday loop, adds each day's folgas to `absent` and passes `weekendWorkerIds` to
   `fillWeekday` for the non-weekend-F evening anchor.

Replan also now seeds the weekly lunch-break counter from the locked past days, so the cap
of 2 breaks spans the whole week rather than resetting for the regenerated part (the
`ScheduleEngineTest` replan test caught a worker getting a 3rd break otherwise).

Because the loop only processes future days, only future folgas are applied; folgas that
fall on locked past days are already reflected there. `computeFolgaDays` is deterministic,
so when the weekend crew is unchanged the future folgas line up with what the locked past
assumed. The overtime trimmer remains as a backstop for the mid-week-change edge cases.

## Test

`ScheduleEngineTest.replan_midWeek_keepsCoverageAndFolgas_noOvertime`: generate a full week,
lock Mon+Tue, replan from Wednesday, and assert a pharmacist every open hour, nobody over
40h, and ≤2 breaks — the whole replan path, previously untested.

## Not changed

The standalone-Sunday regen (Saturday locked, Sunday regenerated) still uses the weekend
rotation, correctly. Replan does not recompute the 4-week hours-balancing seed beyond what
it already loads; out of scope here.
