# Task 25: Add JVM Tests for Ranking, Filtering, and DAO SQL

**Status: Not started.**

Source: Code review — Test coverage gaps. Priority: P2.

## Problem

There are no JVM tests for `NeurhomeRepository` ranking selection/filtering,
`HomeViewModel` query filtering, or the heavy DAO SQL — the riskiest, most
complex code.

## Scope

- Add JVM tests for repository ranking selection and tag filtering.
- Add `HomeViewModel` query-filtering tests.
- Add DAO tests (Robolectric/JVM or instrumentation with a fixture DB) for the
  top-apps SQL and `LIMIT` behavior.

## Acceptance criteria

- New tests cover classic and learned selection, tag filtering, and query
  filtering.
- Tests run under `./gradlew :app:testDevDebugUnitTest` without a personal DB.

## Dependencies

Benefits from Task 16. Relates to Task 10.
