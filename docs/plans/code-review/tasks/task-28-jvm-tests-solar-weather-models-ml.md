# Task 28: Add JVM Tests for Solar, Weather Mapping, Event Boundaries, and ML Parsing

**Status: Not started.**

Source: read-only test-gap audit (reviewer run `e1ac17d0`, commit `4a18604`);
full report `/tmp/neurhome-test-gap-review.md`. These gaps are outside the
original 20 remediation tasks and were surfaced by the dedicated coverage
review. Priority: P2.

## Problem

The audit found high-value, readily JVM-testable code with no coverage:

1. `data/solar/SolarCalculator.calculateSolarTimes` (`SolarCalculator.kt:89-138`)
   — pure JDK math, **zero tests**; the Home Sun card depends on it.
2. `data/weather/WeatherRepository` JSON mapping + `Result` contract
   (`WeatherRepository.kt:14-40,63-68`) — a field rename fails silently
   (`ignoreUnknownKeys = true` + swallowed `Result`), so the weather card can
   render "N/A" forever with no failing test.
3. `data/models/Event.isMultiDay` / `isFirstDayOfMultiDay` (`Event.kt:18-22`) —
   the exact-24h boundary drives `CalendarDAO` per-day row expansion and is
   untested (see Finding P2 below).
4. `data/ml` parsing: `HomeAppContextEncoder.parseLocalTimestamp`
   (`PairwiseHomeAppReranker.kt:89-90`) silently returns `null` for
   offset-form timestamps, so `PairwiseHomeAppTrainingDataFactory` drops rows
   and LEARNED ranking silently degrades to classic; `isUsable`/cutoff behavior
   is unasserted.
5. `ui/components/Weather.kt` `getWeatherIcon` WMO mapping (needs `private` →
   `internal`) and `Navigator.destinations` completeness.
6. `PairwiseHomeAppTrainingDataFactory.build` leakage/pruning guarantees
   (`hiddenPackages`, 4-month prune, `candidateLimit`) are untested — the
   existing "prior-only" test passes for a leaky factory.

## Scope

Add plain JVM tests under `app/src/test/` (no device/emulator, no personal DB):

- **SolarCalculator**: published-reference sunrise/sunset within tolerance;
  ordering invariant (`morningBlueHourStart < … ≤ sunrise ≤ … ≤ sunset ≤
  eveningBlueHourEnd`); longitude-sign regression (`+100` vs `-100`); polar
  nulls without exception; timezone shift.
- **Weather mapping**: decode a captured Open-Meteo payload (note the
  `weather_code` vs `weathercode` asymmetry); a throwing `WeatherService` yields
  `Result.failure`; 1-day/3-day payload contract (coordinates with Task 26,
  which owns the `Weather.kt:83-89` length guard — do not duplicate it).
- **Event boundaries**: exact 24h (`false`), 25h (`true`), null end/originalDtStart
  (`false`), DST fall-back elapsed 25h. Pin current behavior; if the exact-24h
  case is confirmed unintended, fix minimally and report.
- **ML parsing**: legacy space-form vs ISO timestamp; fractional seconds;
  offset string ⇒ `null` (documents the silent-drop path); `isUsable` before/
  after a valid fit; `train(trainingCutoff=…)` exclusion.
- **Training-data factory**: no leakage (a package appearing after T never
  appears at/before T), hidden packages never targets/candidates, 4-month prune,
  `candidateLimit` + deterministic tie-break.
- **Weather icon / Navigator**: table-driven WMO mapping; `Navigator.destinations`
  route set equals `Destination::class.sealedSubclasses` route set.
- **P2 finding**: `Event.isMultiDay` uses elapsed `Duration…toHours() > 24`, so a
  2-day all-day event (exactly 24h) collapses to one row. The test must pin the
  intended behavior; fix only if the intent is unambiguous.

Do not add instrumentation/connected tests. Do not add heavyweight test
dependencies unless unavoidable (prefer the existing JUnit setup); if a new
JVM-safe test dependency is required, add it to `gradle/libs.versions.toml` and
`app/build.gradle.kts` and explain why.

## Acceptance criteria

- New tests cover the paths above and fail if the corresponding regression is
  reintroduced.
- `./gradlew :app:testDevDebugUnitTest` passes without a personal DB.
- `./gradlew :app:assembleDevDebug` succeeds.

## CI (GitHub Actions)

PR CI already runs `./gradlew :app:testDevDebugUnitTest` and
`./gradlew :app:lintDevDebug` (Task 27), so every test added here executes on
GitHub Actions automatically. Keep the tests JVM-only so they remain
Actions-runnable; do **not** move any of them into `connectedDevDebugAndroidTest`
(the PR workflow intentionally excludes device tests).

## Dependencies

Benefits from Task 16 (repository DI) only for repository-level ranking tests;
the solar/weather/Event/ML-parser tests above do not depend on it.
