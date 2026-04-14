CREATE TABLE employee (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    name                TEXT    NOT NULL,
    role                TEXT    NOT NULL,
    last_weekend_worked TEXT
);

CREATE TABLE employee_absence (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    employee_id INTEGER NOT NULL REFERENCES employee(id),
    start_date  TEXT    NOT NULL,
    end_date    TEXT    NOT NULL,
    type        TEXT    NOT NULL,
    note        TEXT
);

CREATE TABLE public_holiday (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    date TEXT    NOT NULL UNIQUE,
    name TEXT    NOT NULL
);

CREATE TABLE schedule_week (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    iso_year     INTEGER NOT NULL,
    iso_week     INTEGER NOT NULL,
    status       TEXT    NOT NULL DEFAULT 'DRAFT',
    generated_at TEXT    NOT NULL,
    published_at TEXT,
    UNIQUE(iso_year, iso_week)
);

CREATE TABLE shift_assignment (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    schedule_week_id INTEGER NOT NULL REFERENCES schedule_week(id),
    employee_id      INTEGER NOT NULL REFERENCES employee(id),
    date             TEXT    NOT NULL,
    start_time       TEXT    NOT NULL,
    end_time         TEXT    NOT NULL,
    break_start      TEXT,
    break_end        TEXT,
    UNIQUE(schedule_week_id, employee_id, date)
);