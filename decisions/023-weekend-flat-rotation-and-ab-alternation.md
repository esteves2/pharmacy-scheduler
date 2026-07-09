# 023 — Flat weekend rotation + A↔B pattern alternation

**Date:** 2026-06-25
**Approved by:** Martim
**Files:** `engine/WeekendAssigner.java`, `engine/ScheduleEngine.java`

## Problem

Two weekend-rotation gaps, both confirmed by a live audit of June 2026:

1. **Uneven frequency.** `pickWeekendWorkers` hard-coded a 2F + 2T crew, picking the 2
   longest-waiting F's and the 2 longest-waiting T's separately. With 5 F and 4 T, this
   under-worked pharmacists (~1.6 weekends/month) vs technicians (~2/month).
2. **No A↔B alternation.** Which worker landed in Pair A (Sat morning + Sun evening) vs
   Pair B (Sat evening + Sun morning) was decided by sort order, never by their previous
   pattern — so "quem trabalha um horário de fds troca o próximo" was not happening.

## What the Excel says

23 real weekends (~5 months): everyone works ~2 weekends/month, F and T equal (pool
averages F 1.96, T 1.93). They achieve this by varying the crew — ~65% of weekends are
2F+2T, but ~22% run **3F + 1T**, which lets the larger F pool keep pace.

## Fix

1. **Flat rotation (`pickWeekendWorkers`).** Pick the **4 longest-waiting workers overall**
   (the list is already sorted longest-since-last-weekend first), then enforce the F floor
   by swapping the most-recently-worked técnicas out for the longest-waiting unpicked
   farmacêuticas until ≥2 F. This naturally produces a 3F+1T weekend whenever pharmacists
   have waited longer — matching the Excel — and evens everyone toward ~2/month.

2. **A↔B alternation (`formPairs` + `ScheduleEngine.computeLastWeekendPattern`).** The
   engine reads each worker's last Saturday shift from the 4-week history (`shift_assignment`,
   start hour < 12 = they were Pair A) into a `Map<Long,Boolean> lastWeekendWasPairA`, passed
   to `assignWeekend`. `formPairs` then assigns each worker the **opposite** of their last
   pattern: one F anchors each pair (F coverage preserved), honoring the two anchors' flip
   when they differ, and the rest fill by flip where the pair has room. No history defaults
   to Pair A. No new DB columns — it reuses the same lookback the fairness sort already loads.

## Limits

- The 1-F-per-pair + 2-per-pair constraints mean perfect individual alternation isn't always
  possible (e.g. both anchor F's flipped to the same side — one must take the other). It's
  best-effort, which is also what a human scheduler does.
- Mid-week holidays don't cross-link Sat/Sun, so `assignHoliday` passes an empty pattern map
  (no alternation there) — unchanged behavior.

## How to verify

Regenerate ~5 consecutive weeks. Expect: each person ~2 weekends/month regardless of role
(F and T roughly equal), some weekends running 3F+1T, and repeat weekend-workers alternating
Sat-morning/Sat-evening (Pair A ↔ Pair B) between their weekends. Still one F per pair, no
new validation errors.
