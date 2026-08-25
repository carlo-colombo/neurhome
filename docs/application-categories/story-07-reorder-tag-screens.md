# Story 7: Reorder Tag Screens

**As a launcher user, I want to shift tag screens left and right so the pager
matches the order I use most often.**

## Implementation Slice

- Add persisted ordering for tags, including the required migration, and DAO/repository operations to move a tag left or right.
- Add controls or gestures for shifting tag screens left and right.
- Rebuild the pager in persisted order after every move.
- Apply the persisted order consistently in the home pager and the dedicated
  Categories destination.
- Keep Uncategorized last and prevent it from being shifted among user tags.
- Handle tag deletion and creation without duplicate or invalid positions.

## Tests

- DAO/repository tests for moving tags in both directions and persisting order.
- ViewModel tests for boundary moves, creation, and deletion.
- Compose tests for left/right controls or gestures and disabled boundary behavior.
- Process-restart test proving tag screen order is restored.
- End-to-end test proving Uncategorized remains last after every reorder.

## Acceptance Criteria

- Tag screens can be shifted left and right.
- The order is persisted and restored after process restart.
- The first tag cannot move left and the last tag cannot move right.
- Uncategorized is always the final screen.
