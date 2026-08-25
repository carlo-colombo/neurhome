# Story 4: Show Tagged Applications

**As a launcher user, I want each tag screen to show its applications in order
so I can launch apps by category.**

## Implementation Slice

- Combine launcher applications with persisted relationships in the repository flow.
- Filter each tag screen to applications assigned to that tag.
- Sort each result alphabetically by application label.
- Render every result through the existing `ApplicationsList`.
- Pass the category view model's application actions through `ApplicationsList` so
  launch, detail, visibility, alias, favourite, tag, and uninstall behavior is
  identical to the full applications screen.
- Keep screen contents reactive when applications or relationships change.
- Keep category collection flows stable across recomposition and load category
  data from launcher applications and tag assignments without waiting for
  unrelated usage-statistics or contacts queries.
- Show applications without tags in the Uncategorized screen.
- Render the same category data from the home pager and the dedicated
  Categories destination.
- Keep the Uncategorized tag controls compact: no screen header, with the input
  and add-tag action on one row.

## Tests

- Repository/ViewModel tests for tag filtering and case-insensitive ordering.
- Tests proving an application assigned to multiple tags appears on each matching screen.
- Compose test that each screen renders through existing application-list behavior and supports scrolling.
- Reactive UI test that assignment changes update the visible screen.

## Acceptance Criteria

- Each tag screen shows only applications assigned to that tag.
- Applications are alphabetically ordered.
- Existing application detail, launch, visibility, alias, favourite, and uninstall actions continue to work.
- Empty tag screens have a clear empty state.
- The Categories destination and home pager remain consistent after data
  changes.
