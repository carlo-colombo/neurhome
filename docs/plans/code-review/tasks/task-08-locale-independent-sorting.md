# Task 8: Make Application/Tag Sorting Locale-Independent

**Status: Not started.**

Source: Code review — Bugs 8. Priority: P2.

## Problem

`applicationAndContacts` sorts with `it.label.lowercase()` (default locale) while
`applicationsWithTags` and `filterApplicationsForTag` use
`String.CASE_INSENSITIVE_ORDER`. Under a Turkish locale this diverges. The same
applies to `tagNamesByApplication` (`String::lowercase`).

## Scope

- Replace default-locale `lowercase()` sorting with `String.CASE_INSENSITIVE_ORDER`
  or `lowercase(Locale.ROOT)` consistently.
- Add a JVM test with a Turkish-locale comparator if practical.

## Acceptance criteria

- All application/tag sort paths use the same locale-independent comparator.
- `./gradlew :app:testDevDebugUnitTest` and `:app:assembleDevDebug` succeed.

## Dependencies

None.
