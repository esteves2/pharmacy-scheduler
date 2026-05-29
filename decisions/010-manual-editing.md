# Decision 010 — Manual schedule editing

## What
Added cell-level editing to the WeekView grid for DRAFT weeks.

## How it works
- `localMap: Record<"date|empId", AssignmentResponse | null>` holds the current edit state, initialised from `weekData` via `useEffect` and reset after every server sync (save, regenerate, replan).
- `null` in the map = shift deleted. Missing key = not yet touched (falls back to weekData).
- Clicking any cell in a DRAFT week opens `AssignmentModal` with 4 time fields (start, end, break start, break end).
- Clicking an assigned cell pre-fills the modal; includes a "Remover turno" delete button.
- Clicking an empty cell opens the modal with sensible defaults.
- "Guardar alterações" button appears (and Publicar is blocked) when `isDirty = true`.
- Save calls `PUT /api/schedules/weeks/{year}/{week}` with the full current assignment list; server response resets local state.
- `id: null` is sent for new assignments (server assigns a real id); existing assignments send their existing id.
- Totals row shows "Total *" with a tooltip when dirty — totals are server-computed and stale until saved.
- Editing is disabled on PUBLISHED weeks (cells are not clickable).

## What was NOT done
- Live recalculation of totals while editing (post-MVP)
- Confirmation dialog when regenerating with unsaved changes (post-MVP)
