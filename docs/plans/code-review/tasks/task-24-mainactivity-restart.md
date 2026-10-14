# Task 24: Replace `MainActivity.restart()` `exitProcess(0)`

**Status: Done.** `restart()` relaunches the task with
`FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` instead of `exitProcess(0)`;
no hard kill remains. Because Room keeps one closed database instance per process
and the repositories hold its DAOs, the replacement rebinds the data layer
(`NeurhomeApplication.rebindDataLayer`) so the relaunched UI reads the new file.
`recreate()` was rejected: it retains the activity's ViewModels, which still hold
the closed database. Verified end to end on the `edge70` emulator: replacing the
database keeps the same process (no `System.exit` in logcat), instantiates a new
Room database, and the settings read from the replacement file are visible.

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

## Manual verification

No automated test covers the rebind (an instrumentation test would need the Room
`room-testing` fixture plumbing). Repro on a device/emulator:

1. Craft a replacement DB whose settings differ from the current ones, e.g.
   `show.alternative.time=true` and `alternative.time.zone=Europe/Rome`.
2. Launch the app, note the PID (or confirm no `System.exit`/`VM exiting` appears
   in logcat afterwards), then Settings → *Replace database* and pick the file.
3. Expect logcat `Replacing database` → `Restarting Neurhome` → `Loading database`
   → `Instantiating database`; the PID must be unchanged and there must be no
   `System.exit`, no `VM exiting`, and no FATAL.
4. Re-open Settings: the replacement values (`UTC`, *Show alternative time*
   unchecked) must be visible, proving the new file is in effect.
