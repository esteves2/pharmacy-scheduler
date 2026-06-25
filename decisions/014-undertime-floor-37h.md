# 014 — Undertime floor lowered from 38h to 37h

**Date:** 2026-06-25
**Approved by:** Martim (explicit)

## Decision

`ShiftTemplates.UNDERTIME_THRESHOLD_HOURS` changed from 38 to 37.

## Reasoning

Everyone targets 40h/week and everyone rotates weekends. A weekend week nets 37h:
3 weekday 8h shifts (24h) + Saturday 7h + Sunday 6h + 2 folgas. With a 13h weekend
pair and 8h weekday shifts, a weekend worker's total is `13 + 8n` — n=3 gives 37h,
n=4 gives 45h. Nothing lands in the 38-40 band. The pharmacy's own Mar-Jun manual
schedules confirm every weekend worker totals exactly 37h.

So a 38h floor was unreachable for anyone working a weekend and produced a false
UNDERTIME warning every week. Martim accepted 37h as the floor rather than changing
shift lengths.

## Scope

- Single constant. No shift-length changes.
- Propagates automatically to `ScheduleValidator` (warning text now reads "abaixo de
  37h"), `ScheduleService` summary status, and `EngineSmokeTest` — all read the
  constant, none hard-code 38.
- Still catches genuine under-allocation: anyone below 37h is flagged.

## How to verify

Generate or open a week with weekend workers (e.g. 2026 W26/W27). Workers at 37.0h
should no longer show an "abaixo de" warning. Anyone genuinely low (e.g. a 13h or 29h
total) should still be flagged.
