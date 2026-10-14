# Task 19: Deduplicate `topAppsByScore` / `simulatedTopApps` SQL

**Status: Done.** The two ranking queries now build their SQL from shared top-level
`private const val` fragments: the `packages`, `counts`, and `scores` CTEs are each stored once
and the two `now` CTEs plus the Wi-Fi terms are appended at the seams where
`simulatedTopApps` adds them. The redundant `order by` inside the `scores` CTE was removed; the
outer `order by score desc` still orders both rankings, and `topAppsByScore` now orders by the
`score` alias (the same expression) so the shared tail applies to both. Changes are confined to
`ApplicationLogEntryDao.kt`; the repository call sites are untouched.

Source: Code review — Refactoring. Priority: P3.

## Problem

`topAppsByScore` and `simulatedTopApps` share ~90% of their CTE; the `ORDER BY`
inside the `scores` CTE is redundant with the outer order.

## Scope

- Extract shared SQL constants/views for the common CTE.
- Remove the redundant inner `ORDER BY`.

## Acceptance criteria

- No duplicated SQL body.
- Ranking output is unchanged.
- `./gradlew :app:assembleDevDebug` succeeds.

## Dependencies

Overlaps with Task 10; coordinate so the two land coherently.
