# Story 2: Create a Tag and Screen from Uncategorized

**As a launcher user, I want to create a tag from Uncategorized so a new,
scrollable category screen is available immediately.**

## Implementation Slice

- Add a Room `Tag` table with a unique tag name.
- Add the migration, schema export, entity, DAO, and repository operations for creating and listing tags.
- Add tag creation controls to Uncategorized.
- Add the new tag screen to the category pager and make its content scrollable.
- Keep Uncategorized as the final screen after the newly created tag.
- Reject blank and duplicate names without creating partial data.

## Tests

- DAO/repository tests for creating, listing, and rejecting duplicate tags.
- Migration/schema test proving existing metadata remains intact.
- ViewModel test that a created tag is emitted as a new screen.
- Compose tests for creation validation, immediate screen availability, pager order, and scrolling.

## Acceptance Criteria

- A valid tag is stored in the database and creates a reachable screen.
- The new screen can be scrolled independently of the pager.
- Uncategorized remains the last screen.
- Blank and duplicate tag names do not modify the database.
