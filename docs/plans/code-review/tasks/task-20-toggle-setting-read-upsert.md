# Task 20: Rewrite `SettingsRepository.toggleSetting` as Read-Then-Upsert

**Status: Done.** `SettingsRepository.toggleSetting` delegates to
`SettingDao.toggle`, a `@Transaction` read-then-upsert/delete that shares one
Room transaction for the read and the write, so the toggle no longer relies on a
thrown `SQLiteConstraintException` for control flow and concurrent toggles
serialize. Behavior is unchanged: an absent key is stored as `"true"`, and a
present key (any value) is deleted. The `SettingDao.insert` overload that lost
its only call site was removed.

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
