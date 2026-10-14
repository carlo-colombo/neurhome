# Task 21: Consolidate ViewModel Creation Wiring

**Status: Not started.**

Source: Code review — Refactoring. Priority: P3.

## Problem

`AppViewModelProvider` duplicates wiring for every ViewModel; the composition
root could supply dependencies in one place.

## Scope

- Introduce a small composition-root module (or a DI framework if the team wants
  Hilt/Koin) to centralize ViewModel dependencies.
- Remove duplicated factory wiring.

## Acceptance criteria

- Single place defines ViewModel dependency graphs.
- No manual duplication per ViewModel.
- `./gradlew :app:assembleDevDebug` succeeds.

## Dependencies

Related to Task 12 and Task 16.
