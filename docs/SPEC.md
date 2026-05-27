# Pharmacy Scheduler — Complete Specification

## Context

Farmácia Esperança, a pharmacy in Santa Cruz, Madeira. 10 staff members, 1 on maternity leave, 9 active. Manual Excel scheduling replaced by a local Java/Spring Boot app with embedded SQLite. No cloud, no auth. User double-clicks a JAR, browser opens to localhost:8080.

## Staff

| Name     | Role | Status    |
|----------|------|-----------|
| Paula    | F    | Active    |
| Nidia    | F    | Active    |
| Jéssica  | F    | Active    |
| Andreia  | F    | Active    |
| Cristina | F    | Active    |
| Sara     | F    | Maternity |
| Natty    | T    | Active    |
| Carolina | T    | Active    |
| Crisanta | T    | Active    |
| Paulina  | T    | Active    |

F = Farmacêutica, T = Técnica. At least 1 F must be present every hour the pharmacy is open. Augusta and Mauricio appear in old Excel files but are not pharmacy counter staff. Exclude them.

No `status` column on the employee table. Maternity is an absence record with type MATERNITY and a date range. The engine checks absences, not employee status.

## Opening Hours

- Mon–Sat: 08:00–22:00 (14h)
- Sun + feriados: 08:00–20:00 (12h)

---

## Shift Templates

### Weekday (Mon–Fri, no feriado)

6 slots per day. Morning shifts are 7h to avoid a 6-person coverage spike during the afternoon handoff.

| Slot | Time  | Break   | Hours worked |
|------|-------|---------|-------------|
| 1    | 8–15  | none    | 7h          |
| 2    | 8–15  | none    | 7h          |
| 3    | 9–18  | 13–14   | 8h          |
| 4    | 10–19 | 14–15   | 8h          |
| 5    | 14–22 | none    | 8h          |
| 6    | 14–22 | none    | 8h          |

46 person-hours per weekday. Breaks stored as `break_start`/`break_end` on `shift_assignment`.

#### Hourly coverage

| Hour  | Slots present              | Count | Min | Target |
|-------|----------------------------|-------|-----|--------|
| 8–9   | 1,2                        | 2     | 2 ✓ | 2 ✓   |
| 9–10  | 1,2,3                      | 3     | 2 ✓ | 3 ✓   |
| 10–13 | 1,2,3,4                    | 4     | 3 ✓ | 4 ✓   |
| 13–14 | 1,2,4 (3 on break)         | 3     | 3 ✓ | 4 ✗   |
| 14–15 | 1,2,3,5,6 (4 on break)     | 5     | 3 ✓ | 4 ✗   |
| 15–18 | 3,4,5,6                    | 4     | 3 ✓ | 4 ✓   |
| 18–19 | 4,5,6                      | 3     | 3 ✓ | 4 ✗   |
| 19–21 | 5,6                        | 2     | 2 ✓ | 3 ✗   |
| 21–22 | 5,6                        | 2     | 2 ✓ | 2 ✓   |

All minimums met. Targets missed at 13–14 (lunch break), 14–15 (break handoff), 18–19, 19–21. Accepted trade-offs. The 14–15 spike to 5 is the highest overshoot — 1 person over target for 1 hour. Acceptable.

### Saturday (08:00–22:00)

Weekday opening hours, reduced staff. 4 workers total in 2 pairs. Each pair has 1 F. No breaks.

| Shift   | Time  | Hours |
|---------|-------|-------|
| Morning | 8–15  | 7h    |
| Evening | 15–22 | 7h    |

28 person-hours. Flat coverage of 2 all day.

### Sunday / Feriado (08:00–20:00)

Reduced hours. 4 workers total in 2 pairs. Each pair has 1 F. No breaks.

| Shift   | Time  | Hours |
|---------|-------|-------|
| Morning | 8–14  | 6h    |
| Evening | 14–20 | 6h    |

24 person-hours. Flat coverage of 2 all day. All 18 holidays use this template — the pharmacy never closes, it operates on Sunday hours.

### Weekend Cross-Linking

Sat/Sun pair assignments cross-link to equalize hours:

- Pair A: Sat morning 8–15 (7h) + Sun evening 14–20 (6h) = **13h**
- Pair B: Sat evening 15–22 (7h) + Sun morning 8–14 (6h) = **13h**

Remaining 5 workers get folga both days.

Mid-week feriados are standalone single-day assignments. No cross-linking.

---

## Headcount Rules

### Weekday (Mon–Sat, no feriado)

| Time Slot | Target | Minimum |
|-----------|--------|---------|
| 08–09     | 2      | 2       |
| 09–10     | 3      | 2       |
| 10–19     | 4      | 3       |
| 19–21     | 3      | 2       |
| 21–22     | 2      | 2       |

Headcount of 3 where target is 4 is normal — happens when someone gets trimmed to avoid overtime.

### Weekend / Feriado

| Time Slot | Target | Minimum |
|-----------|--------|---------|
| All hours | 2      | 2       |

The F constraint (at least 1 farmacêutica present) applies every hour regardless of day type.

## Hours Rules

- Target: 25–40h per week per worker.
- Above 40h: flag red (OVERTIME).
- Below 26h: flag yellow (UNDERTIME).

The 26h floor is derived from the pharmacy's actual slot math. With 9 active employees and 282 total person-hours per week (230h weekdays + 52h weekend), the average is 31.3h/employee. The natural floor — what the lowest-allocated employee receives in a fully-staffed balanced week — is 29h (a weekend worker who draws only two short weekday slots). 10% below that floor is 26.1h, rounded to 26h. This threshold catches genuine under-allocation while ignoring normal rotation variance. Absence days are credited at 8h each (all types except FOLGA) before the threshold check, so employees on sick leave or maternity do not generate false UNDERTIME warnings.

---

## Public Holidays

18 holidays per year for this pharmacy. The pharmacy never closes — all holidays use the Sunday/feriado template (08:00–20:00).

### Fixed (14 dates, same every year)

| Date   | Name                                |
|--------|-------------------------------------|
| Jan 1  | Ano Novo                            |
| Jan 15 | Santo Amaro (municipal, Santa Cruz) |
| Apr 2  | Dia da Autonomia (regional, Madeira)|
| Apr 25 | Dia da Liberdade                    |
| May 1  | Dia do Trabalhador                  |
| Jun 10 | Dia de Portugal                     |
| Jul 1  | Dia da Região (regional, Madeira)   |
| Aug 15 | Assunção de Nossa Senhora           |
| Oct 5  | Implantação da República            |
| Nov 1  | Todos os Santos                     |
| Dec 1  | Restauração da Independência        |
| Dec 8  | Imaculada Conceição                 |
| Dec 25 | Natal                               |
| Dec 26 | Primeira Oitava (regional, Madeira) |

### Easter-derived (4 dates, computed per year)

| Offset from Easter | Name            |
|--------------------|-----------------|
| −47 days           | Carnaval        |
| −2 days            | Sexta-feira Santa |
| 0 days             | Domingo de Páscoa |
| +60 days           | Corpo de Deus   |

Easter computed via the Computus algorithm — a pure function, ~15 lines of code. No external API.

### Holiday Generation

A service method takes a year and returns all 18 dates. On app startup or when navigating to a new year, check if that year's holidays exist in `public_holiday`. If missing, generate and insert. The user can still add or remove holidays through the Holidays page in the UI.

Flyway seeds 2026 and 2027 on first launch. The generator covers every year after that automatically.

---

## Engine Architecture

```
ScheduleEngine          (stateless, pure computation)
  ├── WeekendAssigner   (Phase 1a: Sat/Sun pairs, Phase 1b: mid-week feriados)
  ├── WeekdayFiller     (Phase 2)
  ├── ShiftTrimmer      (Phase 3)
  └── ScheduleValidator (Phase 4)

ScheduleService         (orchestrator, DB reads/writes)
  ├── calls Engine.generate() per week
  ├── persists results to schedule_week + shift_assignment
  └── manages week status transitions (DRAFT → PUBLISHED)
```

The engine takes pure data in (employees, absences, holidays, existing locked assignments) and returns pure data out. No DB access inside the engine. Testable without a database.

### In-Memory Data Structures

**DayPlan** — one day's assignments. Holds a list of `SlotAssignment` objects. Knows its date, whether it's weekend/holiday, which template applies.

**SlotAssignment** — one person in one slot. Employee ref, start/end, break start/end. Not yet persisted.

**WeekAccumulator** — tracks hours-per-employee for the current ISO week. Updated as each day gets planned. The fill algorithm reads from this to balance hours. Seeded from prior weeks' assignments when available (see Hours Balancing Lookback).

**WeekResult** — one week's output: list of `DayPlan`, list of `ValidationMessage`, per-employee hour totals.

**ValidationMessage** — severity (ERROR/WARNING), date, hour (if applicable), description.

### Hours Balancing Lookback

Generation is per-week. The WeekAccumulator handles intra-week balancing. For cross-week fairness, the engine reads the last 4 weeks of assignment history before filling.

Recent cumulative hours feed into the sort as a tiebreaker: recent-cumulative-hours ASC, then weekly-hours ASC, then `id` ASC. This keeps distribution fair across weeks without hard constraints. Not a cap — just a preference for assigning longer slots to employees who've worked fewer recent hours.

---

## Engine Phases

### Phase 1a: Weekend Pair Assignment

For each Saturday/Sunday pair in the week:

1. Filter to available employees (no overlapping absence).
2. Sort by `effectiveLastWeekendWorked` ASC (computed dynamically from 4-week `shift_assignment` lookback, Saturdays only), then `id` ASC.
3. Pick 4. At least 2 must be F (one F per pair).
4. Check if Saturday is a feriado. If no: Pair A gets Sat 8–15 + Sun 14–20, Pair B gets Sat 15–22 + Sun 8–14. If yes: both days use feriado template — Pair A gets Sat 8–14 + Sun 14–20, Pair B gets Sat 14–20 + Sun 8–14.
5. Everyone else gets folga for both days.

Normal weekend totals: 13h per pair (7h + 6h). Saturday-feriado weekend totals: 12h per pair (6h + 6h).

If fewer than 2 F available: assign 1 F per pair where possible. If only 1 F total, put her in one pair, generate a WARNING. If 0 F, generate an ERROR. Never halt generation.

`effectiveLastWeekendWorked` is computed by `ScheduleService.generate()` from the 4-week prior `shift_assignment` lookback (Saturdays only, max date per employee). Passed to `ScheduleEngine.generate()` as a `Map<Long, LocalDate>`. Bootstrap case: first-ever generation has empty map → ID-order picks → self-corrects from week 2 onward.

### Phase 1b: Mid-Week Feriado Assignment

For each feriado falling Mon–Fri:

1. Filter to available employees.
2. Sort by `effectiveLastWeekendWorked` ASC (same fairness rotation), then `id` ASC.
3. Pick 4. At least 2 F.
4. Assign 2 morning (8–14) + 2 evening (14–20). Each pair has 1 F.
5. Everyone else gets folga.

### Phase 2: Weekday Fill

Process each weekday in calendar order. The WeekAccumulator already contains weekend/feriado hours.

For each normal weekday (Mon–Sat, no feriado):

1. Apply the 6-slot template.
2. For each slot, pick from available employees sorted by: recent-cumulative-hours ASC, weekly hours ASC (from WeekAccumulator), then `id` ASC.
3. Skip anyone already assigned that day, absent, or on folga.
4. Ensure at least 1 F present during every open hour.
5. Update the WeekAccumulator after each assignment.

Slot fill order is fixed but arbitrary. The hours-ASC sort does the balancing work. Weekend workers already carry hours in the accumulator, so they get fewer or shorter weekday slots.

### Phase 3: Trim

Runs after Phase 2 produces a full schedule. Fixes overtime.

1. Find every employee over 40h in the week. Process the most over-target employee first. Tiebreak by `id` ASC.
2. For that employee, find assignments where removing them drops headcount from above-minimum to at-minimum (not below).
3. Score each candidate removal by hours saved.
4. Check: does removing this assignment break the F constraint for any hour of that day? If yes, skip.
5. Remove the highest-scoring safe candidate.
6. Recompute the WeekAccumulator.
7. Repeat until the employee is ≤40h or no safe removals remain.
8. Move to next over-target employee.

Trim is iterative. Each removal changes headcount, which may make other removals unsafe. Recompute after every removal.

If someone cannot be trimmed below 40h (e.g. the only available F for multiple days), surface a WARNING.

### Phase 4: Validate

Pure read-only scan of the completed week.

**Per hour of every day:**
- ERROR: 0 farmacêuticas present
- ERROR: headcount below minimum
- WARNING: headcount below target

**Per employee for the week:**
- ERROR (red): hours > 40
- WARNING (yellow): hours < 25

Output: list of `ValidationMessage` objects attached to the `WeekResult`.

Validation runs on every save (PUT) and on publish. Publishing is gated on zero ERRORs. WARNINGs are surfaced but don't block.

---

## Replan

Triggered by: sick leave, emergency absence, mid-week changes. Scoped to a single week.

1. Load existing `schedule_week`.
2. Set `locked_until = today`.
3. Keep all assignments before today. Delete all assignments from today forward.
4. Seed the WeekAccumulator with hours from locked (past) assignments.
5. Load all absences including new ones.
6. Re-run Phases 1–4 for remaining days only.
7. For half-locked weekends (Saturday is past, Sunday is future): keep Saturday's assignments, regenerate Sunday as a standalone day.
8. Re-validate the full week (locked + regenerated).
9. Save as DRAFT. User reviews before publishing.

---

## Absence Types

| Type      | Behaviour                                    |
|-----------|----------------------------------------------|
| FERIAS    | Known in advance, blocked before generation  |
| FOLGA     | Day off                                      |
| SICK      | Triggers replan                              |
| MATERNITY | Long-duration block, same as any absence     |

All types are rows in `employee_absence` with a date range. The engine treats them identically: if an absence overlaps a target date, the employee is unavailable. No status column on the employee.

---

## Database

5 tables. SQLite, single file next to the JAR: `jdbc:sqlite:./pharmacy.db`

```sql
employee(
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  name                  TEXT NOT NULL,
  role                  TEXT NOT NULL             -- 'F' or 'T'
)

employee_absence(
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  employee_id     INTEGER NOT NULL REFERENCES employee(id),
  start_date      TEXT NOT NULL,                  -- ISO date
  end_date        TEXT NOT NULL,                  -- ISO date
  type            TEXT NOT NULL,                  -- FERIAS | FOLGA | SICK | MATERNITY
  note            TEXT                            -- optional
)

public_holiday(
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  date            TEXT NOT NULL UNIQUE,           -- ISO date
  name            TEXT NOT NULL
)

schedule_week(
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  iso_year        INTEGER NOT NULL,
  iso_week        INTEGER NOT NULL,
  status          TEXT NOT NULL DEFAULT 'DRAFT',  -- DRAFT | PUBLISHED
  generated_at    TEXT NOT NULL,                  -- ISO datetime
  published_at    TEXT,                           -- ISO datetime, null until published
  last_edited_at  TEXT,                           -- ISO datetime, updated on every PUT
  UNIQUE(iso_year, iso_week)
)

shift_assignment(
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  schedule_week_id INTEGER NOT NULL REFERENCES schedule_week(id),
  employee_id      INTEGER NOT NULL REFERENCES employee(id),
  date             TEXT NOT NULL,                 -- ISO date
  start_time       TEXT NOT NULL,                 -- HH:MM
  end_time         TEXT NOT NULL,                 -- HH:MM
  break_start      TEXT,                          -- HH:MM, nullable
  break_end        TEXT,                          -- HH:MM, nullable
  UNIQUE(schedule_week_id, employee_id, date)
)
```

No `shift_template` table. Shifts are constants in the engine.
No `monthly_schedule` table. The week is the unit of work. Months are a frontend navigation concept, not a data entity.
No `worker_preferences` table. Deterministic sorting produces consistent schedules.

### Flyway migrations

| Version | File | Purpose |
|---------|------|---------|
| V1 | `create_tables.sql` | All 5 tables |
| V2 | `seed_employees.sql` | 10 employees |
| V3 | `seed_holidays.sql` | 36 holiday rows (2026 + 2027) |
| V4 | `seed_sara_maternity.sql` | Sara's maternity absence |
| V5 | `drop_last_weekend_worked.sql` | Drop `last_weekend_worked` column from `employee` |
| V6 | `add_last_edited_at.sql` | Add `last_edited_at TEXT` to `schedule_week` |

---

## REST API

### Reference Data (CRUD)

| Method | Endpoint                          | Purpose                                 |
|--------|-----------------------------------|-----------------------------------------|
| GET    | /api/employees                    | List all employees                      |
| GET    | /api/employees/{id}               | Get one employee                        |
| PUT    | /api/employees/{id}               | Update employee                         |
| GET    | /api/holidays?year={year}         | List holidays, filtered by year         |
| POST   | /api/holidays                     | Add a holiday                           |
| PUT    | /api/holidays/{id}                | Update a holiday                        |
| DELETE | /api/holidays/{id}                | Remove a holiday                        |
| GET    | /api/absences?from={date}&to={date} | List absences in date range          |
| POST   | /api/absences                     | Create absence                          |
| PUT    | /api/absences/{id}                | Update absence                          |
| DELETE | /api/absences/{id}                | Remove absence                          |

### Schedule Operations (domain endpoints)

| Method | Endpoint                                            | Purpose                                    |
|--------|-----------------------------------------------------|--------------------------------------------|
| GET    | /api/schedules/weeks?year={year}&month={month}      | List weeks for a month with status badges  |
| POST   | /api/schedules/weeks/{isoYear}/{isoWeek}/generate   | Run engine, create DRAFT week              |
| GET    | /api/schedules/weeks/{isoYear}/{isoWeek}            | Fetch week (full rich response)            |
| PUT    | /api/schedules/weeks/{isoYear}/{isoWeek}            | Save full week, re-validate, return result |
| POST   | /api/schedules/weeks/{isoYear}/{isoWeek}/publish    | DRAFT → PUBLISHED. Rejects on ERRORs.     |
| POST   | /api/schedules/weeks/{isoYear}/{isoWeek}/replan     | Freeze past, regenerate future, save DRAFT |
| POST   | /api/schedules/weeks/{isoYear}/{isoWeek}/regenerate | Reset DRAFT to engine output               |

### Guard Clauses

| Condition                              | Response   |
|----------------------------------------|------------|
| Generate a week that already exists    | 409        |
| Regenerate a PUBLISHED week            | 409        |
| Publish with validation ERRORs        | 422        |
| GET a week that doesn't exist         | 404        |
| Employee/absence/holiday not found     | 404        |

Note: PUT (`save`) is allowed on both DRAFT and PUBLISHED weeks. On PUBLISHED, status stays PUBLISHED and `last_edited_at` is updated. The frontend surfaces `last_edited_at > published_at` as a passive staleness indicator (icon or banner — no modal).

---

## Response Shapes

### Schedule Week (GET, PUT, POST generate/replan/regenerate all return this)

```json
{
  "isoYear": 2026,
  "isoWeek": 25,
  "status": "DRAFT",
  "days": [
    {
      "date": "2026-06-15",
      "dayOfWeek": "MONDAY",
      "dayType": "WEEKDAY",
      "assignments": [
        {
          "id": 1,
          "employee": { "id": 3, "name": "Jéssica", "role": "F" },
          "startTime": "08:00",
          "endTime": "15:00",
          "breakStart": null,
          "breakEnd": null,
          "hours": 7.0
        }
      ]
    }
  ],
  "employeeSummaries": [
    {
      "employee": { "id": 3, "name": "Jéssica", "role": "F" },
      "weeklyHours": 38.0,
      "status": "OK"
    },
    {
      "employee": { "id": 7, "name": "Natty", "role": "T" },
      "weeklyHours": 42.0,
      "effectiveHours": 42.0,
      "status": "OVERTIME"
    },
    {
      "employee": { "id": 9, "name": "Crisanta", "role": "T" },
      "weeklyHours": 22.0,
      "effectiveHours": 38.0,
      "status": "OK"
    }
  ],
  "validationMessages": [
    {
      "severity": "WARNING",
      "date": "2026-06-17",
      "hour": 19,
      "message": "Headcount below target"
    }
  ]
}
```

`employeeSummaries` includes every active employee, not just those with assignments. If someone has no shifts, they still appear (weeklyHours: 0, status: UNDERTIME). The frontend renders every employee as a column.

`dayType` is `WEEKDAY`, `SATURDAY`, `SUNDAY`, or `HOLIDAY`. Frontend uses it for display only.

`hours` per assignment is pre-computed, break already subtracted. Frontend never does time math.

`status` on employee summaries is `OK`, `OVERTIME`, or `UNDERTIME`. Maps to no color, red, yellow. Backend computes it.

`effectiveHours` = `weeklyHours` + absence credits (8h per non-FOLGA absence day within the week). The UNDERTIME check runs against `effectiveHours`, not `weeklyHours`, so sick days don't falsely flag as undertimed. Frontend can display both.

### Schedule Week — Write Payload (PUT)

```json
{
  "assignments": [
    {
      "id": 1,
      "employeeId": 3,
      "date": "2026-06-15",
      "startTime": "08:00",
      "endTime": "15:00",
      "breakStart": null,
      "breakEnd": null
    }
  ]
}
```

Flat. `id` is null for new assignments. Backend deletes all existing assignments for that week and replaces with this list in one transaction. Runs validation. Returns the full rich response.

### Employee (GET)

```json
{ "id": 3, "name": "Jéssica", "role": "F" }
```

### Absence (GET)

```json
{
  "id": 12,
  "employee": { "id": 3, "name": "Jéssica", "role": "F" },
  "startDate": "2026-06-15",
  "endDate": "2026-06-19",
  "type": "FERIAS",
  "note": null
}
```

Employee embedded. Date-filtered by query params. The availability calendar fetches one month at a time.

### Holiday (GET)

```json
{ "id": 1, "date": "2026-01-01", "name": "Ano Novo" }
```

Filtered by year. Endpoint is `/api/holidays` (not `/api/public-holidays`).

---

## Schedule Lifecycle

```
(not yet generated)
       │
       ▼
     DRAFT  ◄──── regenerate (reset to engine output)
       │  ▲
       │  └── PUT (user edits, stays DRAFT)
       │
       ▼
   PUBLISHED ◄──── PUT (user edits, stays PUBLISHED, last_edited_at updated)
```

Both DRAFT and PUBLISHED weeks accept PUT. The difference:
- **DRAFT**: status stays DRAFT. Normal editing flow.
- **PUBLISHED**: status stays PUBLISHED. `last_edited_at` is written. `published_at` stays frozen. The frontend can compute staleness: `last_edited_at > published_at`.

To do a structural regeneration of either a DRAFT or PUBLISHED week, use **replan** (freezes past assignments, regenerates from today forward, saves as DRAFT).

**regenerate** resets a DRAFT to the current engine output (deletes and re-runs generate). Blocked on PUBLISHED weeks (409).

---

## Tech Stack

### Backend

- Java 21
- Spring Boot 4.x
- SQLite via `sqlite-jdbc`
- Maven (single module)

### Frontend

- React 18
- Vite (build tool)
- Tailwind CSS (styling)
- Built output served from `src/main/resources/static/`

### Build

Maven with `frontend-maven-plugin`. One `mvn package` produces a fat JAR with the React frontend baked in. No Node.js needed at runtime.

The `frontend-maven-plugin` handles:
1. Install Node.js locally (project-scoped, not system-wide)
2. Run `npm install`
3. Run `npm run build`
4. Output lands in `src/main/resources/static/`

Note: `frontend-maven-plugin` is not yet added to `pom.xml`. Add it when frontend work begins.

### Database Migrations

Flyway. SQL scripts in `src/main/resources/db/migration/`. See Flyway migrations table above.

---

## Project Structure

```
pharmacy-scheduler/
├── pom.xml
├── docs/
│   └── SPEC.md             (this file)
├── frontend/               (not yet created)
│   ├── package.json
│   ├── vite.config.js
│   ├── tailwind.config.js
│   ├── index.html
│   └── src/
│       ├── App.jsx
│       ├── pages/
│       │   ├── SchedulePage.jsx
│       │   ├── AvailabilityPage.jsx
│       │   ├── EmployeesPage.jsx
│       │   └── HolidaysPage.jsx
│       ├── components/
│       │   ├── Layout.jsx          (sidebar + content shell)
│       │   ├── WeekGrid.jsx
│       │   ├── DayRow.jsx
│       │   ├── ShiftCell.jsx
│       │   ├── EmployeeSummary.jsx
│       │   └── ValidationPanel.jsx
│       └── api/
│           └── client.js           (fetch wrappers)
└── src/main/
    ├── java/com/farmacia/scheduler/
    │   ├── PharmacySchedulerApplication.java
    │   ├── engine/
    │   │   ├── ScheduleEngine.java
    │   │   ├── WeekendAssigner.java
    │   │   ├── WeekdayFiller.java
    │   │   ├── ShiftTrimmer.java
    │   │   ├── ScheduleValidator.java
    │   │   └── model/
    │   │       ├── DayPlan.java
    │   │       ├── DayType.java
    │   │       ├── Severity.java
    │   │       ├── SlotAssignment.java
    │   │       ├── ValidationMessage.java
    │   │       ├── WeekAccumulator.java
    │   │       └── WeekResult.java
    │   ├── service/
    │   │   ├── ScheduleService.java
    │   │   ├── HolidayGeneratorService.java
    │   │   └── exception/
    │   │       ├── ScheduleAlreadyExistsException.java
    │   │       ├── ScheduleAlreadyPublishedException.java
    │   │       ├── ScheduleHasValidationErrorsException.java
    │   │       └── ScheduleNotFoundException.java
    │   ├── api/
    │   │   ├── GlobalExceptionHandler.java
    │   │   ├── ScheduleController.java
    │   │   ├── EmployeeController.java      (not yet built)
    │   │   ├── AbsenceController.java       (not yet built)
    │   │   ├── HolidayController.java       (not yet built)
    │   │   └── dto/
    │   │       ├── AssignmentResponse.java
    │   │       ├── AssignmentWriteRequest.java
    │   │       ├── DayResponse.java
    │   │       ├── EmployeeDto.java
    │   │       ├── EmployeeSummaryResponse.java
    │   │       ├── ValidationMessageResponse.java
    │   │       ├── WeekResponse.java
    │   │       └── WeekWriteRequest.java
    │   ├── holiday/
    │   │   ├── HolidayCalendar.java
    │   │   └── HolidayDate.java
    │   ├── model/
    │   │   ├── AbsenceType.java
    │   │   ├── Employee.java
    │   │   ├── EmployeeAbsence.java
    │   │   ├── PublicHoliday.java
    │   │   ├── Role.java
    │   │   ├── ScheduleWeek.java
    │   │   ├── ShiftAssignment.java
    │   │   └── WeekStatus.java
    │   ├── config/
    │   │   ├── LocalDateConverter.java
    │   │   └── LocalDateTimeConverter.java
    │   └── repository/
    │       ├── AbsenceRepository.java
    │       ├── EmployeeRepository.java
    │       ├── HolidayRepository.java
    │       ├── ScheduleWeekRepository.java
    │       └── ShiftAssignmentRepository.java
    └── resources/
        ├── application.properties
        └── db/migration/
            ├── V1__create_tables.sql
            ├── V2__seed_employees.sql
            ├── V3__seed_holidays.sql
            ├── V4__seed_sara_maternity.sql
            ├── V5__drop_last_weekend_worked.sql
            └── V6__add_last_edited_at.sql
```

The engine package has zero Spring dependencies except `@Component` on `ScheduleEngine`. The service layer bridges engine and DB. The API layer handles HTTP and DTO conversion.

---

## Dev Workflow

During development, two processes run:

1. **Vite dev server** on port 5173: `cd frontend && npm run dev`. Hot-reloads React changes instantly.
2. **Spring Boot** on port 8080: run via IntelliJ or `.\mvnw.cmd spring-boot:run`. Serves the API.

Vite proxies API calls to Spring Boot. In `vite.config.js`:

```js
export default defineConfig({
  server: {
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
})
```

The developer opens `http://localhost:5173` in the browser. Frontend requests to `/api/*` are proxied to the Spring Boot backend. No CORS config needed.

For production, `mvn package` builds the frontend, copies it into the JAR, and everything runs on port 8080.

---

## App Bootstrap

Spring Boot `ApplicationRunner` opens the browser on startup:

```java
@Component
public class BrowserLauncher implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) throws Exception {
        Thread.sleep(1500); // let server finish starting
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(new URI("http://localhost:8080"));
        }
    }
}
```

If `Desktop` isn't supported (headless environment), log a message and continue. The app still works — the user opens the browser manually.

---

## Frontend Pages

### Schedule (main page)

Week grid. Rows = days (Mon–Sun), columns = employees. Each cell shows a shift block (color-coded by role) or folga. Weekly hour totals per employee along the bottom row, color-coded by status (OK/OVERTIME/UNDERTIME). Validation messages in a collapsible panel below the grid. Generate/Save/Publish buttons top-right.

Month navigation as a secondary view: 4–5 week rows with status badges (DRAFT/PUBLISHED/not generated). Click a week to edit it.

### Availability

Absence calendar. Rows = employees, columns = dates. Cells show absence type or empty (available). Click a cell or drag a range to create an absence. Click an existing one to delete it. Filtered by month. This is what the user checks before generating a week.

### Employees

Simple table of employees. Name, role. Click a row to edit in a slide-over panel. No status column — availability is managed through the Availability page.

### Holidays

List of holidays for the selected year. Auto-generated but editable. Add or remove individual holidays. Rarely touched after initial setup.

### Sidebar Navigation

Fixed left sidebar, content area to the right. Four links: Schedule, Availability, Employees, Holidays.

---

## Known Issues

### Validation message ordering non-deterministic

`HashMap` iteration order causes validation messages to appear in different order across runs. Cosmetic. Fix post-MVP by switching to `LinkedHashMap` or sorting by severity + date.

### `EngineSmokeTest` not run by Maven

`EngineSmokeTest` is a `main` method, not JUnit. `mvn test` reports 0 tests from it. Must be run explicitly via IntelliJ right-click → Run. Risk: engine regressions won't surface in CI. Post-MVP: convert to proper JUnit parameterized tests.

### `buildWeekResponse()` duplicates DayPlan reconstruction

`publish()` reconstructs `DayPlan` objects for validation, then calls `buildWeekResponse()` which reconstructs them again from the same DB data. Harmless but fragile — the two reconstruction paths could silently diverge. Post-MVP: extract a private helper that reconstructs once and passes the result to both validation and response-building.

---

## Key Decisions

1. 6 weekday slots with mixed durations. Morning shifts shortened to 7h to cap the worst-case overshoot at 5 (one hour, +1 over target).
2. Trim priority: most over-target employee first, tiebreak by ID.
3. Mid-week feriados assigned as standalone days using the Sunday/feriado template.
4. Half-locked weekends during replan: keep the locked day, regenerate the unlocked day solo.
5. F shortage fallback: assign what you can, generate warnings/errors, never halt.
6. Fill-then-trim, not greedy-with-caps.
7. Jéssica (ID 3) is the pharmacy manager. For MVP, treated as normal F — no special engine or display logic. Post-MVP: add `scheduling_priority` column (NORMAL/MANAGER) to `employee`; MANAGER employees sort last in Phase 2 (gap-filler only), are exempt from UNDERTIME flagging, and still count as F for coverage. Weekend rotation unchanged — she rotates normally. Cristina likely shares the same contract type ("igual Jéssica" in Excel annotations) and may also get MANAGER priority post-MVP.
8. Week is the unit of work. No monthly_schedule table. Months are a frontend query.
9. CRUD for reference data. Domain endpoints for schedule operations.
10. PUT saves the full week grid in one shot. No individual assignment CRUD.
11. Backend validates on every save. Publishing gated on zero ERRORs. WARNINGs don't block.
12. Cross-week hour fairness via 4-week lookback. Tiebreaker, not a hard constraint.
13. No dirty-flag optimization. Validation is cheap, correctness matters more.
14. No employee status column. Maternity is an absence with a date range. One code path for all absence types.
15. All schedule responses use the same rich nested shape. Backend computes everything. Frontend is a display layer.
16. Flat write payload for PUT. Rich nested response for reads. No round-tripping embedded objects.
17. Holiday generator: pure function, year in, 18 dates out. 14 fixed + 4 Easter-derived. Covers national, Madeira regional, and Santa Cruz municipal holidays.
18. React + Vite + Tailwind frontend. Built by Maven, embedded in the JAR. No Node.js at runtime.
19. Flyway for database migrations. Seeds employees and holidays on first launch.
20. Single GET returns the full week: assignments nested by day, employee summaries, validation messages. One request, one render, always consistent.
21. Hours thresholds: 26h floor, 40h ceiling. Floor derived from slot math: 282h total / 9 employees = 31.3h average, natural minimum 29h, 10% below = 26h. Absence days credited at 8h each (non-FOLGA) before the check to suppress false UNDERTIME on sick/maternity weeks.
22. PUBLISHED weeks are editable via PUT. Status stays PUBLISHED, `last_edited_at` updates. Staleness surfaced passively in the UI. Replan is for structural regeneration only.
23. Weekend rotation tracks `effectiveLastWeekendWorked` dynamically from `shift_assignment` history. No column on `employee`. Lookback reads all weeks regardless of status.
