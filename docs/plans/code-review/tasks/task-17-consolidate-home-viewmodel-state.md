# Task 17: Consolidate Home ViewModel State and Make It Immutable

**Status: Done.** `homeUIState` is now a two-level `combine`: a private
`HomeContent` holder combines the five section flows, then a single 3-arg
`combine(homeContent, weatherUIState, sunUIState)` builds one `HomeUIState` per
emission with no intermediate `.copy()`. `FilteredUIState.apps` is a `val`, and
`FilteredUIState.loading` defaults to `false`, matching its actual (synchronous,
already-loaded) emissions.

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
