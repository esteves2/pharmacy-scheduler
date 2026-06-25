# 013 — Schedule Analysis Gaps (Excel comparison, 2026-06-25)

## Context

After comparing the program's output against the client's real Excel schedule, three gaps were
found. All three were confirmed by Martim ("yes") on 2026-06-25.

---

## Gap 1 — Missing shift templates (9-17, 10-18)

### Finding
The Excel schedule uses `9-17` (139 occurrences), `10-18` (36 occurrences), and `11-19`
(38 occurrences) heavily. The program only had `9-18 with break` and `10-19 with break`, missing
pure 8h no-break variants.

### Decision
Replace the 6-slot `WEEKDAY_SLOTS` with an 8-slot structure:

| Slot | Start | End   | Break        | Notes                              |
|------|-------|-------|--------------|------------------------------------|
| 0    | 08:00 | 16:00 | —            | Essential morning (duplicate)      |
| 1    | 08:00 | 16:00 | —            | Essential morning (duplicate)      |
| 2    | 09:00 | 17:00 | —            | New — primary mid-morning          |
| 3    | 10:00 | 18:00 | —            | New — primary mid-morning late     |
| 4    | 14:00 | 22:00 | —            | Essential evening (duplicate)      |
| 5    | 14:00 | 22:00 | —            | Essential evening (duplicate)      |
| 6    | 09:00 | 18:00 | 13:00–14:00  | Optional overflow (7th person)     |
| 7    | 10:00 | 19:00 | 14:00–15:00  | Optional overflow (8th person)     |

**Assumption**: `11-19` was dropped in favour of `9-17` and `10-18`. The real schedule shows
`9-17` (139x) far more often than `11-19` (38x). If the client needs `11-19` it can be added
as slot 8 in a follow-up.

**Assumption**: Slots 6 and 7 (break slots) are kept as optional overflow for the 7th and 8th
employee of the day. The engine fills them only if an employee is available; no warning is
raised if they remain unfilled.

### Validator/Trimmer threshold change
With slot 3 ending at 18:00 (previously 19:00), the minimum headcount boundary moves:

- Old: `if (hour < 10) 2; if (hour < 19) 3; else 2`
- New: `if (hour < 10) 2; if (hour < 18) 3; else 2`

This matches the real schedule where 2 people cover 18:00–22:00.

---

## Gap 2 — Two contract tiers (37h vs 40h)

### Finding
The Excel reveals two groups:
- **40h Mon-Fri only**: Andreia (4), Nídia (2), Crisanta (8), Natty (6)
- **37h with weekend rotation**: Paula (1), Jéssica (3), Cristina (5), Carolina (7), Paulina (9)

### Decision
Add `contract_hours INTEGER NOT NULL DEFAULT 40` column to `employee`. Seed known employees
via V10 migration:

```
37h: Paula(1), Jéssica(3), Cristina(5), Carolina(7), Paulina(9)
40h: Nídia(2), Andreia(4), Natty(6), Crisanta(8), Sara(10)
```

**Assumption**: The IDs (1-10) match production data. If IDs differ, the V10 migration will
silently no-op for non-matching rows — contractHours will stay at the default 40. The admin
can correct values through the employee edit UI.

**Known limitation for MVP**: The WeekendAssigner does not yet restrict weekend assignments to
37h employees only. Gap 3 mitigates this by giving folgas only to those who both worked the
weekend AND have contractHours == 37. Full weekend restriction is post-MVP.

---

## Gap 3 — Programmatic weekday folga rotation

### Finding
When a 37h employee works the weekend, they receive 2 consecutive weekday folgas that week.
These are NOT stored as absence records — they are computed at schedule-generation time.

### Decision
After Phase 1a (weekend assignment) in `ScheduleEngine.generate()`, compute programmatic folgas:

1. Identify 37h employees who worked the weekend (Saturday assignments this week).
2. Assign 2 consecutive weekday folgas, spreading evenly across Mon-Fri (max 2 per day).
3. Merge into `absentEmployeeIds` for each weekday fill call.

**Scope**: Applied only in `generate()`, not `replan()`. Replan locks past days and only fills
future ones; re-computing folgas mid-week could conflict with existing locked assignments.

**UNDERTIME warning**: In a weekend week, a 37h employee works Sat(7h) + Sun(6h) + 3 weekdays(24h)
= 37h, which is below the `UNDERTIME_THRESHOLD_HOURS = 38`. This will produce an UNDERTIME
warning. Accepted as a known MVP limitation — the 37h employee is working their contracted hours.
A post-MVP fix should either lower the threshold to 37 or make it per-contract-type.

**Folga distribution algorithm**: Workers sorted by ID. For each worker, scan Mon-Thu in order
and assign the first consecutive pair where both days have fewer than 2 folgas already assigned.
This caps daily absence at 2, preventing more than 2 workers being absent on the same day.
