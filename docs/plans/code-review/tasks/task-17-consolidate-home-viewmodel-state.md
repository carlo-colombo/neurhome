# Task 17: Consolidate Home ViewModel State and Make It Immutable

**Status: Not started.**

Source: Code review — Refactoring. Priority: P3.

## Problem

`homeUIState` is a 7-flow `combine` chain producing five intermediate
`HomeUIState` copies. `FilteredUIState.apps` is a mutable `var`, and loading
defaults do not reflect actual loading.

## Scope

- Reduce the number of combines (or introduce a single state holder).
- Make `FilteredUIState.apps` a `val`.
- Make loading defaults accurate.

## Acceptance criteria

- Home behavior and content are unchanged.
- No mutable fields in UI state classes.
- `./gradlew :app:testDevDebugUnitTest` and `:app:assembleDevDebug` succeed.

## Dependencies

None.
