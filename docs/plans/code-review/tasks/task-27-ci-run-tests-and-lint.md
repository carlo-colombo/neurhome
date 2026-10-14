# Task 27: Run Tests and Lint in CI

**Status: Not started.**

Source: Code review — Test coverage gaps / CI. Priority: P2.

## Problem

Per `AGENTS.md`, CI only builds; it does not run tests or lint, so the
regressions above are caught only locally.

## Scope

- Add `:app:testDevDebugUnitTest` (and lint) to the pull-request workflow.
- Keep the instrumentation tests out of PR CI unless a device/emulator is
  available.

## Acceptance criteria

- PR CI runs unit tests and lint and fails on regressions.
- Build times remain reasonable.

## Dependencies

Benefits from Tasks 25 and 26 existing.
