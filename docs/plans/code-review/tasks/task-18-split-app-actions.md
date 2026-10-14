# Task 18: Split the `AppActions` Grab-Bag

**Status: Not started.**

Source: Code review — Refactoring. Priority: P3.

## Problem

`INeurhomeViewModel.AppActions` (`NeurhomeViewModel.kt:21`) bundles six unrelated
callbacks, so unrelated rows recompose and testing is harder.

## Scope

- Split into focused callback holders (or pass individual lambdas).
- Update `ApplicationItem`, `ApplicationList`, `CategoryPager`, `HomeScreen`,
  `HomeApplicationsList`, and `WatchArea` call sites.

## Acceptance criteria

- Unrelated rows no longer recompose when one action changes.
- No behavior change.
- `./gradlew :app:assembleDevDebug` succeeds.

## Dependencies

None.
