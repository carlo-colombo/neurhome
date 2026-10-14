# Task 9: Gate and Debounce Home Model Training on `LEARNED`

**Status: Done.** Training is gated on `LEARNED`, debounced 2 s per burst, and
throttled to at most once per 15 minutes. Review fixes: skip refreshes while a
training is already in flight, and guard the `homeModel` write with a monotonic
`modelTrainingGeneration` so a superseded run cannot write back after `CLASSIC`.
Remaining NIT: training still runs for `LEARNED` without its runtime
prerequisites (location) — report-only, matches the task spec.

Source: Code review — Performance 1. Priority: P0.

## Problem

`logLaunch` calls `refreshHomeModel()` (`NeurhomeRepository.kt:363`) and `init`
calls it too (`:334`). It reads `applicationLogEntryDao.all()` (unbounded,
~106k rows in the sample DB) and runs O(launches x labels) training on every
launch, even in `CLASSIC` mode. This is the single biggest CPU/battery cost.

## Scope

- Gate training on `homeAppSelection == LEARNED`.
- Debounce/throttle refreshes so a burst of launches does not retrain each time.
- Keep training off the write/launch critical path.

## Acceptance criteria

- No training runs while selection is `CLASSIC`.
- Repeated launches within the debounce window do not retrain.
- Home ranking still updates for `LEARNED` within a bounded delay.
- `./gradlew :app:testDevDebugUnitTest` and `:app:assembleDevDebug` succeed.

## Dependencies

Related to Task 4 (cancellable training) and Task 10 (SQL limits).
