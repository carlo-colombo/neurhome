# Task 10: Add `LIMIT`/Precomputed Score to Top-Apps SQL

**Status: Done.** Both queries take `LIMIT :limit`. Home over-fetches with
`homeCandidateLimit(n) = n * 8 + 16` (64 for `n = 6`) because quiet-profile and
uninstalled-app filtering happen after the SQL cut. The statistics simulation is
bounded by an explicit `SIMULATED_TOP_APPS_SQL_LIMIT = 100`. Precomputing a
`now`-independent score was not required and is left as a possible follow-up.

Source: Code review — Performance 2. Priority: P1.

## Problem

`topAppsByScore()` / `simulatedTopApps()` (`ApplicationLogEntryDao.kt`) use
non-sargable `min(abs(t - now.minuteOfTheDay), ...)` in `WHERE`, self-joins, and
return all matching rows while Kotlin only takes 6
(`NeurhomeRepository.kt:290-311`). The `LEARNED` path also runs `mostUsedApps()`
(a full 4-month scan) every 30 s tick.

## Scope

- Add `LIMIT 6` (or the required N) to the queries.
- Ideally precompute a `now`-independent score, or otherwise make the predicate
  sargable.
- Avoid running both `mostUsedApps()` and `topAppsByScore()` every tick when one
  suffices.

## Acceptance criteria

- Queries return only the needed rows; no full self-join result is materialized
  in Kotlin.
- Ranking output is unchanged for representative fixtures.
- `./gradlew :app:testDevDebugUnitTest` and `:app:assembleDevDebug` succeed
  (consider an instrumentation/DAO test against a fixture DB).

## Dependencies

None.
