# Task 22: Encapsulate Application State and Refresh Profiles

**Status: Not started.**

Source: Code review — Refactoring. Priority: P3.

## Problem

`NeurhomeApplication` exposes `cb`, `wifiContext`, `wifiLoggingEnabled`, and
`positionLoggingEnabled` as `internal var`/`@Volatile` mutated from extensions.
Separately, `ApplicationService.profiles` is snapshotted in the constructor and
goes stale when work profiles change.

## Scope

- Introduce a small state holder for the application-level mutable state and
  funnel mutations through it.
- Re-read profiles from `LauncherApps` or register a change callback instead of
  snapshotting once.

## Acceptance criteria

- Application state is not freely mutable from extensions.
- Profile add/remove is reflected without a process restart.
- `./gradlew :app:assembleDevDebug` succeeds.

## Dependencies

Related to Task 15.
