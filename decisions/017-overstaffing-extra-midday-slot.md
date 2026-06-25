# 017 — Absorb the idle worker with an extra 09-17 midday slot

**Date:** 2026-06-25
**Approved by:** Martim (go-ahead for bug #4; placement decided from the Excel)
**File:** `engine/WeekdayFiller.java`

## Problem

With 9 schedulable staff and 8 fixed weekday slots, full-attendance days leave one
worker idle. Same-slot-all-week makes that the non-owner (a weekend worker), so they get
fewer weekday shifts than needed and land under 37h — W26's Nidia at 29h. The shortfall
is exactly one weekday slot:

- 5 non-weekend workers × 5 days = 25 slots consumed at 40h each.
- Weekday capacity = 4 folga-days × 7 present + 1 full day × 8 = 36 slots.
- 36 − 25 = 11 slots for 4 weekend workers; they need 12 (3 each → 37h). Short by 1.

Martim's rule allows overstaffing ("não faz mal ter pessoas a mais"), so we add the
missing slot rather than accept the gap.

## Placement — decided from `horario analise.xlsx`

Weekday shift frequencies across ~3 months (Augusta/Mauricio excluded):

| Shift | Count | Band |
|-------|-------|------|
| 14-22 | 222 | evening |
| 8-16  | 212 | morning |
| 10-19 (break) | 74 | midday |
| 9-18 (break)  | 73 | midday |
| 9-17  | 41 | midday |
| 11-19 | 38 | midday |
| 10-18 | 36 | midday |

Morning and evening sit at exactly 2/day and are never overstaffed. Every spare body in
the real schedules lands in the **midday band**. The two break shifts are already the
engine's overflow slots 6/7; the most common **straight** midday shift is **09-17**.
So the extra (9th) body takes 09-17: no lunch break (respects no-break-twice), and it
reinforces the 09-10 ramp.

## Fix

After the 8 standard slots are filled, add one 09-17 assignment when:
- an available employee is still unassigned that day (true overstaffing — never fires on
  folga days where ≤8 are present), and
- their weekly hours + 8 ≤ 40 (no overtime).

The lowest cumulative-hours unassigned worker is chosen, so the extra shift goes to the
week's most under-allocated person. W26: Nidia gets a Friday 09-17 → 29h becomes 37h.

## Scope / limits

- One extra body max per day (only 9 schedulable, 8 slots → at most 1 idle).
- Holiday weeks: per Martim, accept the lower hours. The rule simply finds no spare
  worker to place (fewer people available), so it does not compensate — as intended.
- If an under-allocated worker is already above 32h, the +8h would exceed 40h and the
  slot is not added; they stay below 37h. Rare; unavoidable without partial shifts.
- No ownership recorded for the extra slot; the greedy lowest-hours pick lands on the
  same non-owner across full days naturally.

## How to verify

Regenerate W26: Nidia should reach 37h (a 09-17 appears on the full-attendance day) and
the `abaixo de 37h` warning should clear. Holiday week W27 will still show some workers
under 37h — accepted. No new errors; no one over 40h.
