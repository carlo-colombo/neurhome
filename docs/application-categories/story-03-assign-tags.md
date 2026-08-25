# Story 3: Assign Tags from Application Detail

**As a launcher user, I want to assign one or more tags from application detail
so I can categorize applications across multiple screens.**

## Implementation Slice

- Add the application/tag relationship table, keyed by package, profile, and tag, with a uniqueness constraint.
- Add the relationship migration, schema export, DAO, and repository methods.
- Add a multi-select tag control to the existing expanded application detail.
- Load available tags and current assignments, and persist additions and removals.
- Support zero tags, one tag, and multiple tags.
- Complete the end-to-end flow from detail selection through category screen state updates.
- Keep assignment updates visible from both the home category pager and the
  dedicated Categories destination.

## Tests

- DAO/repository tests for adding, removing, and listing relationships, including package/profile isolation and duplicate assignment prevention.
- ViewModel tests for zero, one, and multiple selected tags.
- Compose test for selecting and clearing tags in application detail.
- End-to-end test proving assignment updates Uncategorized and relevant tag screens after persistence.

## Acceptance Criteria

- Tags are shown in alphabetical order in application detail.
- The user can select and persist multiple tags or clear all tags.
- Assignments survive detail reopening and process restart.
- An assigned application leaves Uncategorized and appears on every assigned tag screen.
- Both category entry paths observe the same persisted assignments.
