# 021 — Remove the dead contract_hours feature

**Date:** 2026-06-25
**Approved by:** Martim ("clean up the code, remove dead code")

## What

Removed the `contract_hours` field end to end:

- `Employee.contractHours` (entity field)
- `EmployeeDetailDto.contractHours` (API field)
- `EmployeeController` — the setter on update and the getter in `toDetail`
- `frontend/src/api/types.ts` — `contractHours` on `EmployeeDetailDto`
- `frontend/src/pages/EmployeesPage.tsx` — the "Horas contratuais" 40h/37h selector,
  its state, and the update payload field
- New migration `V11__drop_contract_hours.sql` drops the column (SQLite 3.49 via
  sqlite-jdbc 3.49.1.0 supports `DROP COLUMN`)

## Why

The column encoded a two-tier contract model (fixed 37h vs 40h employees) that turned out
to be a misread of the real schedule — everyone is 40h and everyone rotates weekends
(decisions 012, 016). The engine stopped reading it when the folga gating was removed
(016), leaving a UI selector that wrote a value nothing consumed. Dead and misleading, so
removed rather than left to rot.

## Notes

- Migrations V9 (add) and V10 (seed) stay in history; V11 reverses them forward.
- The frontend must be rebuilt (`mvn package` or `npm run build`) for the selector to
  disappear from the bundle; the built `static/` is gitignored and regenerates.
- The engine has no unused imports left from the earlier refactors (the removed F-helper
  methods were already deleted in decision 015; remaining imports are wildcards, all used).
