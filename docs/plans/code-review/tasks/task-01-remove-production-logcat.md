# Task 1: Remove or Gate the Production `logcat` Process

**Status: Done.** Gated behind `BuildConfig.DEBUG`. Verified the generated
`prodRelease` `BuildConfig` sets `DEBUG = false`, so the spawn is unreachable in
production (R8 removes the dead branch).

Source: Code review — Bugs 1. Priority: P0.

## Problem

`NeurhomeApplication.onCreate()` unconditionally runs
`Runtime.getRuntime().exec(arrayOf("logcat", "-f", <externalFilesDir>/app_logcat.txt, ...))`
(`application/NeurhomeApplication.kt:147-152`). In production this keeps a
process alive writing private launch/context data to external storage and
consumes battery and space.

## Scope

- Delete the logcat spawn from the application `onCreate`, or gate it behind
  `BuildConfig.DEBUG` (dev flavor) so it never runs in `prod`.
- If kept for dev, ensure the file is cleaned up and the process is not started
  repeatedly.

## Acceptance criteria

- No `logcat` child process is spawned in a `prod` build.
- Dev builds still run if the team relies on the capture, or the artifact is
  removed entirely.
- `./gradlew :app:assembleDevDebug` succeeds.

## Dependencies

None.
