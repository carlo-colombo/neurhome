# Task 24: Replace `MainActivity.restart()` `exitProcess(0)`

**Status: Not started.**

Source: Code review — Refactoring. Priority: P3.

## Problem

`MainActivity.restart()` (`MainActivity.kt:92-99`) kills the process with
`exitProcess(0)`.

## Scope

- Prefer `recreate()` or a proper relaunch intent.
- Verify the restart trigger still applies the intended state change.

## Acceptance criteria

- Restart no longer hard-kills the process.
- The triggering setting/flow still takes effect.
- `./gradlew :app:assembleDevDebug` succeeds and manual check.

## Dependencies

None.
