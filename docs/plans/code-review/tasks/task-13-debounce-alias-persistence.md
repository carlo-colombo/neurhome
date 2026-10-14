# Task 13: Debounce Alias Persistence

**Status: Not started.**

Source: Code review — Performance 5. Priority: P2.

## Problem

`ApplicationItem.kt` `TextField` calls `appActions.setAlias(app, it)` per
character, producing one DB upsert per keystroke.

## Scope

- Persist alias on collapse/focus loss, or debounce writes.
- Keep the displayed value responsive while typing.

## Acceptance criteria

- Typing does not issue one DB write per character.
- The final alias is persisted after editing ends.
- `./gradlew :app:assembleDevDebug` succeeds and manual check on device.

## Dependencies

Related to Task 5 (alias state keying).
