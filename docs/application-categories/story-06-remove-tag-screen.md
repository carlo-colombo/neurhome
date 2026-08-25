# Story 6: Remove a Tag Screen

**As a launcher user, I want to remove a tag screen so obsolete categories no
longer appear in the pager.**

## Implementation Slice

- Add a remove-tag action to the tag screen with confirmation.
- Delete the tag and all of its relationships transactionally.
- Remove the screen from the pager and keep the current page valid.
- Remove the screen from the dedicated Categories destination as well as the
  home pager.
- Move applications with no remaining tags to Uncategorized.

## Tests

- Repository test for transactional tag and relationship deletion.
- ViewModel test for removing a tag and rebuilding screen state.
- Compose tests for confirmation, cancellation, and screen removal.
- End-to-end test proving other tags and their assignments are unchanged.

## Acceptance Criteria

- Removing a tag requires explicit confirmation.
- Confirming removes the tag screen and all assignments to that tag.
- Cancelling leaves the tag and its assignments unchanged.
- Uncategorized and remaining tag screens reflect the deletion immediately.
