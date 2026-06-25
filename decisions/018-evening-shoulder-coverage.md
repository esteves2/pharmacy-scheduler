# 018 — Cover the 18-19 evening shoulder (swap overflow slots 6/7)

**Date:** 2026-06-25
**Approved by:** Martim ("we want people staffing the pharmacy when possible")
**File:** `engine/ShiftTemplates.java`

## Problem

The `Equipa abaixo do objectivo (2 < 3)` INFO on folga days is the 18:00-19:00 shoulder:
only the two 14-22 evening workers are on then, so headcount = 2 vs target 3. It shows
Mon-Thu (the 2-folga days) and not Friday, which happens to staff a 10-19 worker.

Verified against the real Excel (109 normal weekdays): the manual schedule covers 18-19
with >=3 **every** day (102 days at 3, 7 at 4, zero under). So this is a real engine gap,
not an inherent limitation — the prior "leave it" call was wrong. The Excel achieves it
by always staffing a 10-19 shift (the `10-14 15-19` break shift, its most common midday
slot, 74 occurrences).

## Fix

The engine already defines a 10-19 break shift, but as the very last overflow slot (7),
so it only fills when 8 people are present — never on 2-folga days. Swapped slots 6 and 7
so the 10-19 shift sits at index 6 and fills on 7-person folga days; the 9-18 shift moves
to index 7 (last). No new shift, no new break (both were already break shifts), no change
to the morning {0,1} / evening {4,5} anchors.

7-person folga-day coverage after the swap: every hour meets target, including 18-19
(10-19 + two 14-22 = 3) and 09-10 (two 08-16 + 09-17 = 3). The 13-14 lunch dip even
eases on these days since the filled break is now 14-15, not 13-14.

## Why not redefine a slot as a new break shift

Making an always-filled (essential) slot a break shift would, under same-slot-all-week,
give its owner a lunch break every weekday — violating "no lunch break 2x/week". The swap
avoids this: it does not add a break-shift owner, it only reorders which overflow shift
fills first.

## Related issue (NOT fixed here — flagged to Martim)

Same-slot-all-week already gives the owner of a break slot (6 or 7) a lunch break on every
day they work. A non-weekend owner would break 5x/week; even weekend owners hit 3x
(e.g. Carolina in W26). That appears to breach "no lunch break 2x/week". The real Excel
rotates break/late shifts across people to respect the cap. Resolving it means relaxing
strict same-slot-all-week for break shifts — a larger change, pending Martim's call on how
hard the 2x rule is.

## How to verify

Regenerate W25/W26. The `2 < 3` INFO should disappear (18-19 now covered). No new errors;
hours unchanged (the 10-19 is still an 8h shift).
