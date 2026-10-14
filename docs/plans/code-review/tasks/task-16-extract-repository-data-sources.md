# Task 16: Extract Repository Data Sources for JVM Testability

**Status: Not started.**

Source: Code review — Refactoring. Priority: P3.

## Problem

`NeurhomeRepository` takes `NeurhomeApplication`, `AppDatabase`, `LauncherApps`,
and `UserManager` directly (`NeurhomeRepository.kt:80-90`), so ranking/filtering
logic cannot be unit-tested on the JVM.

## Scope

- Extract interfaces/data sources for Android- and DB-backed inputs.
- Keep domain ranking/filtering logic free of Android types.
- Migrate existing call sites; keep behavior identical.

## Acceptance criteria

- Ranking/filtering logic can be exercised by a plain JVM test with fakes.
- `./gradlew :app:testDevDebugUnitTest` and `:app:assembleDevDebug` succeed.

## Dependencies

Enables Task 25. Related to Task 21.
