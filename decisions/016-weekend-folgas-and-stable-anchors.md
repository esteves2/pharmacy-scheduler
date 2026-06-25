# 016 — Weekend folgas for everyone + non-weekend pharmacist anchors

**Date:** 2026-06-25
**Approved by:** Martim (go-ahead for bug #3)
**Files:** `engine/ScheduleEngine.java`, `engine/WeekdayFiller.java`

## Problem

After fixing evening F-coverage (decision 015), W26 showed **Nidia 53h** and W27 kept
**Andreia 45h**. Weekend workers were overshooting 40h, and others were starved (13h/29h).

## Root cause

Compensating weekday folgas were handed out only to employees with
`contractHours == 37`:

```java
.filter(emp -> emp.getContractHours() != null && emp.getContractHours() == 37)
```

Contract tiers do not exist (see decisions/012, REQUIREMENTS.md) — everyone is 40h and
everyone rotates weekends. Nidia is seeded 40h, so when she worked the weekend she got
zero folgas, worked all five weekday evenings (which 015 pinned her to), and hit 53h.

## Fix

1. **`ScheduleEngine` Phase 1c:** removed the `== 37` filter. Every weekend worker now
   gets 2 compensating weekday folgas, landing them at ~37h.
2. **`WeekdayFiller`:** the morning/evening F anchors now prefer a pharmacist who is NOT
   working this weekend (`weekendWorkerIds` passed in). A non-weekend F owns each anchor
   slot for the whole week, so coverage holds on the weekend-workers' folga days. A stable
   F is reserved for the evening anchor before the day slots fill.

Hand-traced W26 result: Jéssica/Andreia/Cristina (non-weekend F) anchor morning, a day
slot, and evening at 40h each; Crisanta/Paulina (non-weekend T) at 40h; Paula/Nidia/
Natty/Carolina (weekend) at 37h. No overtime, no undertime, F present every hour.

## Scope / limits

- **Replan** does not recompute weekend rotation or folgas (pre-existing gap), so it
  receives an empty `weekendWorkerIds` and behaves as before. Not addressed here.
- The `contract_hours` column, V9/V10 migrations, the `Employee.contractHours` field, the
  EmployeeController mapping, and the frontend "40h/37h" selector still exist. Only the
  folga-gating *logic* was removed. Full teardown is a separate cleanup.
- Degenerate days with fewer than 2 non-weekend F's available (e.g. two on vacation) can
  fall back to a weekend F as an anchor; rare, flagged by the validator if coverage fails.
- More folga-days now means some optional overflow slots (6/7) may go unfilled on
  2-folga days, surfacing "Could not fill slot ..." WARNINGS. Non-blocking. If noisy, a
  follow-up can stop warning for the optional overflow slots.

## How to verify

Regenerate W26 and W27. Expect both to drop to **zero errors**: no `Nenhuma farmacêutica
presente`, no `ultrapassa 40h`. Weekend workers should read 37h, non-weekend staff 40h.
Some `abaixo do objectivo` INFO and possibly overflow "could not fill" warnings may
remain — both non-blocking.
