# Story 5: Remove a Tag from Application Detail

**As a launcher user, I want to remove a tag from application detail so I can
change an application's categorization without deleting the tag.**

## Implementation Slice

- Make selected tags removable in the existing detail multi-select control.
- Delete the corresponding relationship through the DAO and repository.
- Update affected screens and Uncategorized reactively.
- Preserve all other assignments for the application.

## Tests

- DAO/repository test for removing one relationship while retaining others.
- ViewModel test for removing one tag from a multi-tag application.
- Compose test for deselecting a tag and confirming the updated selection.
- End-to-end test proving the app disappears from only the removed tag screen.

## Acceptance Criteria

- Removing one tag does not remove other tags from the application.
- The application disappears from the removed tag screen immediately.
- If no tags remain, the application appears on Uncategorized.
