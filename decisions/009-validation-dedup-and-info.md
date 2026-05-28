# Decision 009 — Validation deduplication and INFO severity

## What changed
- Added `INFO` to `Severity` enum
- "Headcount below target" violations demoted from WARNING → INFO
- Added `consolidate()` post-processing step to `ScheduleValidator`: identical messages (same severity + text) across multiple hours/days collapse into one entry with a `[×N — d1, d2, ...]` suffix
- Frontend ValidationPanel header shows "N notas" badge (blue) for INFO items alongside existing error/warning badges

## Why
Martim's requirement: being above minimum but below target is not a warning — at most a footnote. Repeated identical messages should appear once, not 50 times.

## Consolidation logic
Group by `severity + message text`. If a group has N > 1 entries:
- Collect unique dates from the group
- Replace the group with one message: `original text [×N — d1/MM, d2/MM, ...]`
- date and hour on the consolidated message are null (shown without a date prefix in the UI)

## What stays as-is
- "Headcount below minimum" → still ERROR (pharmacy legally can't open understaffed)
- "No farmacêutica present" → still ERROR
- Per-employee overtime/undertime → still WARNING (one per employee, no dedup needed)
