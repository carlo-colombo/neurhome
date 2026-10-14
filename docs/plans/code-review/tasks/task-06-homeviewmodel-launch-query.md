# Task 6: Honor or Remove `HomeViewModel.launch`'s `query` Parameter

**Status: Not started.**

Source: Code review — Bugs 6. Priority: P2.

## Problem

`HomeViewModel.kt:342-345` always passes `this.query.value` and discards the
`query` argument, so the parameter is misleading.

## Scope

- Either use the passed-in `query` for the launch filter, or remove the
  parameter and its call-site arguments.

## Acceptance criteria

- No dead/misleading parameter remains.
- Launch filtering behavior is unchanged (or intentionally corrected).
- `./gradlew :app:testDevDebugUnitTest` and `:app:assembleDevDebug` succeed.

## Dependencies

None.
