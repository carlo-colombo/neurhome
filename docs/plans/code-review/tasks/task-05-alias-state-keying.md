# Task 5: Key Alias Editing State on Application Identity

**Status: Done.** `ApplicationManagement` now keys on `app.stableListKey()`.
`stableListKey()` is built from package/profile/launcher component only (never the
alias), so typing is unaffected and row reuse resets correctly.

Source: Code review — Bugs 5. Priority: P1.

## Problem

`ApplicationItem.kt:243` uses `var alias by remember { mutableStateOf(app.alias) }`
without keying on the app. On row reuse or an external alias update the
`TextField` shows a stale value. Tags already key on `app.stableListKey()` at
line 133.

## Scope

- Key the alias remembered state on `app.stableListKey()`.
- Verify the initial tag edit set remains separate from persisted state
  (per `AGENTS.md` guidance for application detail).

## Acceptance criteria

- Changing the underlying app causes the alias field to reset to that app's alias.
- No regression in tag editing on the same row.
- `./gradlew :app:assembleDevDebug` succeeds and manual check on device.

## Dependencies

None.
