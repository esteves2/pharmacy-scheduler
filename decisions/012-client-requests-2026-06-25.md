# Client requests — 2026-06-25

Decisions and assumptions made when implementing the 7 requests from the client meeting.

---

## 1. 8h shifts where possible, no lunch breaks

**Change:** `ShiftTemplates.WEEKDAY_SLOTS` slots 0 and 1 change from `08:00–15:00` (7h, no break) to `08:00–16:00` (8h, no break).

Slots 2 and 3 (`09:00–18:00` with 13:00–14:00 break and `10:00–19:00` with 14:00–15:00 break) keep their breaks — removing them would require a 9h continuous slot to cover the same spread, which is worse for staff.

Slots 4 and 5 (`14:00–22:00`) were already 8h with no break — unchanged.

**Assumption:** "8h where possible" means the two 7h morning slots. The client's answer ("I asked for additional schedules if that helps") was interpreted as: overlap is acceptable, not that we should add more slot templates.

---

## 2. No consecutive lunch-break shifts (same employee)

**Confirmed by client:** "consecutive" means across weeks, not consecutive days.

**Implementation:** When filling the first day of a week (Monday, or the earliest unfilled weekday during replan), employees who had a break-shift in the immediately prior ISO week are deprioritized for break slots (slots 2 and 3). They are still eligible if no non-break-veteran candidate exists.

The check is `priorWeekBreakEmployees` — computed in `ScheduleEngine` from the most-recent prior week's `ShiftAssignment` rows where `break_start IS NOT NULL`.

---

## 3. Same hours for a whole week (excludes reduced-hours days)

**Implementation:** `WeekdayFiller` now accepts a `Map<Integer, Long> slotOwnerForWeek` (slot index → employee ID). The first time a slot is filled in a week (Monday, or first available weekday), the chosen employee is recorded as the slot owner via `putIfAbsent`. On subsequent days, the slot owner is tried first; if absent, normal selection applies without recording a new owner.

The rule explicitly excludes public holidays that fall mid-week (those are handled by `WeekendAssigner` with different slot templates). Reduced-hours days (Saturdays and holidays) are not part of weekday slot assignments.

**For replan:** locked assignments from past days of the same week seed the `slotOwnerForWeek` map. Matching is done by `(startTime, endTime, breakStart)` tuple against `WEEKDAY_SLOTS`. For the two pairs of identically-timed slots (0+1 at 08:00–16:00 and 4+5 at 14:00–22:00), the first locked assignment encountered claims the lower-indexed slot.

---

## 4. Min 38h, aim 40h — more overlap OK

**Change:** `ShiftTemplates.UNDERTIME_THRESHOLD_HOURS` 26 → 38.

With all 6 slots at 8h and a 5-day week, a full-week employee accumulates exactly 40h. The `ShiftTrimmer` cap stays at 40h. The validator now raises a warning below 38h effective hours.

---

## 5. Holidays counter per employee — 22 days, current year

**Implementation:** `EmployeeController` injects `AbsenceRepository` and batches a FERIAS query for the current calendar year on every `/api/employees` call. The computed `holidaysUsed` (working days in FERIAS absences) and `holidaysRemaining` (22 − used, clamped at 0) are included in `EmployeeDetailDto`.

**Assumption:** "22 days" counts calendar days of FERIAS absences, not working days only. The absence model stores `start_date`/`end_date`; days = `end_date − start_date + 1`. If working-day counting is required later, this will need a calendar-aware calculation.

---

## 6. Birthday as separate absence category

**Change:** `AbsenceType.BIRTHDAY` added.

**Frontend:** `AbsenceModal` includes "Aniversário" as a selectable type.

**Assumption:** Birthday absences are entered manually — there is no auto-generation of a BIRTHDAY absence on the employee's birthday each year. Auto-generation deferred to post-MVP.

`BIRTHDAY` absences do NOT count toward the 22-day FERIAS entitlement (confirmed by client).

---

## 7. Birthday field on employee

**Change:** `employee.birthday` column (`TEXT`, nullable) added via migration V8. `Employee` model and `EmployeeDetailDto` include the field. The Employees page shows a date picker for it.
