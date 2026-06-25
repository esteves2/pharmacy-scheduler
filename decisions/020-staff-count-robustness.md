# 020 — Make generation scale to any staff count (no rebuild on leave/return)

**Date:** 2026-06-25
**Approved by:** Martim ("works for every scenario, not something I repack when someone
goes on leave or returns")
**File:** `engine/WeekdayFiller.java`

## Problem

The overstaffing absorber (decision 017) added exactly **one** extra slot per day. Tuned
for 9 schedulable staff (one idle on full days). When Sara returns from maternity → 10
staff, a full day leaves **two** idle, so the absorber missed one and that worker fell
under 37h. The engine must not assume a fixed headcount — staff go on leave and return as
absence records, with no app rebuild.

## Fix

1. **Multi-extra overstaffing.** Replaced the single extra with a loop: keep adding a
   straight midday shift for the most under-allocated still-idle worker while one exists
   whose extra 8h keeps them ≤ 40h. Absorbs any surplus (10, 11, … staff) and naturally
   adds zero on folga/understaffed days (no idle worker). For 9 staff, behaviour is
   unchanged (one extra on full days).
2. **Extra-shift variety.** Successive extras cycle through `09-17`, `11-19`, `10-18` —
   the three common straight midday shifts in the Excel — spreading coverage instead of
   stacking identical shifts. All no-break, so the break budget is untouched.
3. **Quieter under-staffing.** "Could not fill slot" now warns only for the 6 essential
   slots; empty optional overflow (6/7) under low staffing is expected and the validator's
   headcount/F checks already flag real coverage gaps.

## Why this covers the scenarios

- **Return from leave (more staff):** surplus bodies are absorbed at ≤40h each → everyone
  stays in the 37–40 band.
- **Going on leave (fewer staff):** fewer people fill fewer slots; essential gaps warn,
  headcount/F thresholds flag under-coverage. Degrades gracefully, no crash, no overtime.
- **Steady state (9):** unchanged from the verified W25/W26 output.

Generation reads availability from absence records, so leave/return needs no code change —
only the absence row. This decision removes the last hard dependency on headcount = 9.

## Not covered here

**Replan** (mid-week sick-day regeneration) still does not recompute programmatic folgas
and passes an empty weekend-worker set. It is a separate feature, not on the leave/return
path (those go through full-week generate), and its partial-week locked state makes a folga
fix non-trivial. Left as a documented gap pending real use.

## How to verify

Add an employee or end Sara's maternity absence so 10 are schedulable, then generate: every
worker should land 37–40h with two extra midday shifts appearing on full days. Remove staff
and generate: schedule still produces, with essential-slot warnings / headcount flags where
genuinely short. The standard 9-staff weeks (W25/W26) should be byte-for-byte unchanged.
