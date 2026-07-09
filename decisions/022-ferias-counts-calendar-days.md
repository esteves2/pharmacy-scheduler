# 022 — Férias counted in calendar days, not working days

**Date:** 2026-06-25
**Decided by:** Martim

## Decision

The 22-day vacation entitlement is charged in **calendar days**: a férias range that
spans a weekend counts the Saturday/Sunday too. No code change — `EmployeeController
.computeHolidaysUsedThisYear` already counts `ChronoUnit.DAYS.between(from, to) + 1`
(inclusive calendar days) over FERIAS absences.

## Rationale

The pharmacy operates 7 days a week and staff rotate weekends, so a weekend inside a
booked vacation is genuinely time taken off. Counting calendar days keeps the rule simple
and means the employee "takes those days off" as booked.

## Revisit if

The pharmacy asks for *dias úteis* (working-days-only) accounting — the standard
Portuguese 22-working-day entitlement. That would be a small change: count only Mon–Fri
(or only days the employee would otherwise be scheduled) within each FERIAS range. Not
done now.

## Note

Only FERIAS absences count toward the 22. FOLGA, SICK, MATERNITY, and BIRTHDAY do not —
the birthday day off is a separate grant, as intended.
