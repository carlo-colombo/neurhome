# Story 1: Enter Uncategorized

**As a launcher user, I want to open an Uncategorized screen so I can access
applications that have not been organized.**

## Implemented Slice

- Added `CategoryPager` to the home destination with home at page 0 and
  Uncategorized at page 1.
- Added a title-only `UncategorizedScreen`; application contents are deferred to
  Story 4.
- Added a Categories icon beside Settings and Statistics in the full
  applications screen.
- Added a dedicated Categories navigation destination containing Uncategorized.
- Kept the existing calendar/weather dashboard pager inside Home unchanged.
- Moved the Wi-Fi logging startup collector after `settingsRepository`
  initialization so the dev build can start reliably.

## Interaction Note

The home page contains another horizontal pager for calendar and weather.
Nested horizontal gesture handling can consume a category swipe, especially
over the dashboard or keyboard. The Categories destination is the reliable
manual-testing entry point until category gestures are separated from the
dashboard gesture surface.

## Implemented Tests

- Compose tests verify the category pager reaches Uncategorized after a left
  swipe and that Uncategorized is the final page when no tags exist.
- The dev test host is debug-only so pager tests can set content without
  changing the production activity.

## Follow-up Tests

- Add an end-to-end test for the Categories icon in the full applications
  screen.
- Add a device-level regression test covering both category and
  calendar/weather gestures.

## Acceptance Criteria

- The home category pager has Home followed by Uncategorized when no tags
  exist.
- Uncategorized is reachable from the Categories icon in the full applications
  screen without requiring the all-applications list to remain open.
- The Uncategorized title is visible and the screen is title-only in this
  slice.
- The existing home calendar/weather behavior remains available.
