# Story 1: Enter Uncategorized by Swiping Left

**As a launcher user, I want to swipe left from home into an Uncategorized
screen so I can access applications that have not been organized.**

## Implementation Slice

- Extend the category swipe surface so a left swipe from home enters a view from the right.
- Add the Uncategorized screen as the only category screen initially.
- Keep this first slice title-only; showing Uncategorized applications is delivered later.
- Add a Categories icon beside Settings and Statistics in the full applications list.
- Open a dedicated categories screen from that icon, containing Uncategorized.

## Tests

- Compose test that a left swipe from home reaches Uncategorized.
- Compose test that Uncategorized enters from the right and is the final page when no tags exist.
- Regression test that the existing home calendar/weather swipe behavior still works.
- Compose test that the Categories icon from the full applications list opens the categories screen.

## Acceptance Criteria

- A left swipe from home opens Uncategorized.
- Uncategorized is reachable without opening the existing all-applications navigation destination.
- Uncategorized is also reachable from the Categories icon in the all-applications screen.
- The screen title is visible and the existing home behavior remains available.
