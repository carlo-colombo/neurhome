# Task 23: Remove the Stale `app/app/schemas/` Directory

**Status: Not started.**

Source: Code review — Refactoring. Priority: P3 (trivial).

## Problem

`app/app/schemas/` (with `ovh.litapp.neurhome3.data.AppDatabase`) is a leftover
empty/duplicate directory. The tracked schema output is `app/schemas`.

## Scope

- Confirm nothing references `app/app/schemas`, then delete the directory.
- Do not remove the tracked `app/schemas` history.

## Acceptance criteria

- `app/app/schemas` no longer exists; `app/schemas` untouched.
- `./gradlew :app:assembleDevDebug` succeeds.

## Dependencies

None.
