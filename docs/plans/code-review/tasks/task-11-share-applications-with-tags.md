# Task 11: Share the `applicationsWithTags` Flow

**Status: Not started.**

Source: Code review — Performance 3. Priority: P2.

## Problem

`applicationsWithTags` is a cold `combine` (`NeurhomeRepository.kt:192-198`);
every `TagScreen` subscription re-maps and re-sorts all apps.

## Scope

- `shareIn` the flow once (application-scoped scope with `WhileSubscribed`), or
  derive all per-tag lists from a single shared list.
- Remember the collected flow on the UI side per `AGENTS.md` (do not create a
  new flow during recomposition).

## Acceptance criteria

- All tag pages derive from one shared, cached application list.
- No visible flicker on category pages.
- `./gradlew :app:assembleDevDebug` succeeds and manual category swipe check.

## Dependencies

None.
