# Task 7: Remove or Fix Dead/Misleading DAO API

**Status: Not started.**

Source: Code review — Bugs 7. Priority: P2.

## Problem

`ApplicationLogEntryDao.resetAndImport` (`ApplicationLogEntryDao.kt:20-23`) is
named like an import-replace but never calls `cleanUp()`; `cleanUp()` has no
callers. `insertAll` is only used by that dead method.

## Scope

- Remove `resetAndImport`, `cleanUp`, and `insertAll` if truly unused, or make
  `resetAndImport` actually clear before importing.
- Confirm no callers via a project-wide search before deleting.

## Acceptance criteria

- No unused/misleading DAO methods remain.
- `./gradlew :app:assembleDevDebug` and unit tests succeed.

## Dependencies

None.
