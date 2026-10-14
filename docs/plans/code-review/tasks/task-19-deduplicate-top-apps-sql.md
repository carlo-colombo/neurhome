# Task 19: Deduplicate `topAppsByScore` / `simulatedTopApps` SQL

**Status: Not started.**

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
