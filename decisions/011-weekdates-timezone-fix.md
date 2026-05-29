# Decision 011 — weekDates timezone fix

## Problem
`weekDates()` used `d.toISOString().slice(0, 10)` to build date strings.
`toISOString()` returns UTC. Portugal is UTC+1 in summer, so midnight local = 23:00 UTC
the previous day — every date came out one day too early, shifting all columns by one.

The bug existed before the grid flip but was invisible: the old day-row layout happened
to show Monday as an all-dashes row (nobody works Monday in this schedule), so the
one-day offset was never noticed.

## Fix
Build the date string from local date components:

```ts
const y   = d.getFullYear()
const m   = String(d.getMonth() + 1).padStart(2, '0')
const day = String(d.getDate()).padStart(2, '0')
return `${y}-${m}-${day}`
```

## Rule going forward
Never use `toISOString().slice(0, 10)` to get a local calendar date.
Use local getters (`getFullYear`, `getMonth`, `getDate`) or append `T00:00:00`
when parsing to force local-time interpretation.
