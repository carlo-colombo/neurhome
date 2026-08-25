# Story 2: Create a Tag and Screen from Uncategorized

**As a launcher user, I want to create a tag from Uncategorized so a new,
scrollable category screen is available immediately.**

## Implementation Slice

- Add a Room `Tag` table with a unique tag name.
- Add the migration, schema export, entity, DAO, and repository operations for creating and listing tags.
- Add tag creation controls to Uncategorized.
- Add the new tag screen to the category pager and make its content scrollable.
- Register the new screen in both category entry paths: the home category pager
  and the dedicated Categories destination.
- Keep the Home dashboard as the first page only in the home entry path; the
  dedicated Categories destination starts directly with the created tags.
- Keep Uncategorized as the final screen after the newly created tag.
- Render each tag name as a centered, fixed header above its scrollable content.
- Reject blank and duplicate names without creating partial data.

## Tests

- DAO/repository tests for creating, listing, and rejecting duplicate tags.
- Migration/schema test proving existing metadata remains intact.
- ViewModel test that a created tag is emitted as a new screen.
- Compose tests for creation validation, immediate screen availability, pager order, and scrolling.

## Acceptance Criteria

- A valid tag is stored in the database and creates a reachable screen.
- The new screen can be scrolled independently of the pager.
- The home pager and dedicated Categories destination show the same screen
  order and state for their shared tag and Uncategorized pages; only the home
  pager includes the Home dashboard page.
- Uncategorized remains the last screen.
- Tag names are centered in a header that does not scroll with the page content.
- Blank and duplicate tag names do not modify the database.
