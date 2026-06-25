# 019 — Window ownership + weekly break budget of 2

**Date:** 2026-06-25
**Approved by:** Martim ("do what you think is correct after your findings")
**Files:** `engine/ScheduleEngine.java`, `engine/WeekdayFiller.java`

## Decision (data-driven)

Two client instructions conflict: "same shift all week" vs "no lunch break more than 2x
a week." Resolved by what the real Excel actually does (175 person-week cases, ≥3 weekday
shifts):

- ≤2 lunch breaks/week: honored in **97%** (169/175) — the hard rule.
- Identical shift all week: only **51%** (89/175), and only for no-break shifts (8-16,
  14-22). Midday people keep the same *window* but flip between the break and a straight
  version to stay under 2 breaks.

So: **keep same-window + ≤2 breaks; sacrifice strict identical-shift.**

## Fix

A `Map<Long,Integer> breaksThisWeek` is created per week in `ScheduleEngine` (generate and
replan) and threaded into `WeekdayFiller.fillWeekday`. When a worker is assigned a break
slot:

- under 2 breaks so far → take the lunch break, increment the counter;
- at 2 → swap to the no-break variant of the same window:
  - late slot `10-19 (break 14-15)` → `11-19` straight (still covers the 18-19 shoulder),
  - midday slot `09-18 (break 13-14)` → `09-17` straight.

Both variants are 8h, so hours are unchanged. The worker keeps their slot/window all week;
only the lunch break is rationed. This is exactly the Excel pattern and it makes the
decision-018 slot 6/7 swap correct (the always-filled 10-19 owner no longer breaks daily).

Emergent bonus: `computeHadBreakShiftLastWeek` will flag this week's break-takers, so the
break-heavy slot naturally rotates to a different owner next week.

## Scope / limits

- Cap is hard at 2; the real Excel slips to 3 about 3% of the time, so the engine is
  marginally stricter — in the intended direction.
- Replan: `seedSlotOwners` matches locked assignments to slots by (start,end,breakStart).
  A stored straight variant (e.g. 11-19) won't match the 10-19 break template, so the
  owner may not be recognized on replan. Replan is already an incomplete path (no folgas,
  empty weekend set); noted, not fixed here.
- Only slots 6 (10-19) and 7 (09-18) carry breaks, so only they are affected.

## How to verify

Regenerate W25/W26. The 18-19 shoulder stays covered (INFO clears), no worker shows more
than two `…/…` split shifts in a week, and weekly hours are unchanged. No new errors.
