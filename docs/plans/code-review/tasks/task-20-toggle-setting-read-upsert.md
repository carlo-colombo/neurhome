# Task 20: Rewrite `SettingsRepository.toggleSetting` as Read-Then-Upsert

**Status: Not started.**

Source: Code review — Refactoring. Priority: P3.

## Problem

`toggleSetting` (`SettingsRepository.kt:61-75`) relies on `INSERT` throwing
`SQLiteConstraintException` to implement a toggle.

## Scope

- Read current value, then insert/delete (or upsert) explicitly.
- Preserve atomic/race-safe behavior; use a transaction if needed.

## Acceptance criteria

- Toggle never relies on a thrown constraint exception for control flow.
- Behavior is unchanged, including concurrent toggles.
- `./gradlew :app:testDevDebugUnitTest` and `:app:assembleDevDebug` succeed.

## Dependencies

None.
