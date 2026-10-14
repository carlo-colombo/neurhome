# Task 26: Add Tests for Weather Lifecycle, Wi-Fi Capture, Tag Races

**Status: Done.** `WeatherForecastTest` and the instrumented `WeatherForecastRowTest`
cover the parallel `Daily` lists (including the short-response crash in `Weather`),
`WifiContextTest` and `ApplicationLoggingStateTest` cover the Wi-Fi capture path, and
`TagRepositoryTest` covers tag positions under interleaved create/move/delete.
`./gradlew :app:testDevDebugUnitTest` passes.

Source: Code review — Test coverage gaps. Priority: P3.

## Problem

No tests cover `WeatherServiceImpl` lifecycle, the `wifiState` capture path, or
`TagRepository` position invariants under create/move/delete races
(`TagRepositoryTest` covers only basics).

## Scope

- Test `WeatherServiceImpl` creation/close behavior (ties into Task 12).
- Test the Wi-Fi state capture path.
- Test tag position invariants under interleaved create/move/delete.

## Acceptance criteria

- New tests fail if the corresponding regression is reintroduced.
- `./gradlew :app:testDevDebugUnitTest` passes.

## Dependencies

Related to Task 12 and Task 22.
