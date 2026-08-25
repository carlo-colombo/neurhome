# Story 8: Save Tag Changes When Detail Closes

**Status: Implemented**

**As a launcher user, I want tag changes to be saved when I close application
detail so category screens do not change while I am still editing.**

## Implementation Slice

- Keep the application's initial tag assignments and edited selection separate
  while application detail is open.
- Apply tag additions and removals only when application detail is closed.
- Persist the complete final selection in one repository operation.
- Keep category screens unchanged while tag edits are in progress.
- Update Uncategorized and assigned tag screens after the detail screen closes.
- Preserve the behavior for zero, one, and multiple tags.

## Tests

- [ ] ViewModel test proving tag edits remain local until detail closes.
- [x] Repository test proving the final selection replaces the previous assignments.
- [ ] Compose test proving category screens do not update while detail is open.
- [ ] End-to-end test proving category screens update after detail closes, including
  moving an application into and out of Uncategorized.
- [x] Close-without-changes behavior is covered by the detail close guard.

## Implementation Notes

- Application detail is the expanded row in `ApplicationItem`; the row keeps the
  initial assignment and edited selection in separate remembered states.
- Tag chips update only the edited selection. The final selection is submitted
  once when the row is collapsed, and no submission occurs when it is unchanged.
- `TagRepository.setTags` replaces all assignments for the application in one
  Room transaction, including the empty selection.

## Acceptance Criteria

- [x] Selecting or deselecting a tag does not immediately change any category screen.
- [x] Closing application detail persists the complete edited tag selection.
- [x] After detail closes, category screens reflect the saved selection.
- [x] Reopening detail shows the saved selection.
- [x] Closing detail without edits leaves the application's assignments unchanged.
