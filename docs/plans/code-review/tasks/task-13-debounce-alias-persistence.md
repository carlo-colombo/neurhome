# Task 13: Debounce Alias Persistence

**Status: Done.** `ApplicationManagement` keeps the alias in local Compose state
while typing and persists it once editing ends: `onFocusChanged` on the field
writes when it loses focus, and a `DisposableEffect(stableKey)` writes when the
management panel leaves the composition (collapse, navigation, LazyColumn
recycling). A `savedAlias` snapshot makes both triggers idempotent, so typing
writes nothing, closing an untouched row writes nothing, and clearing the field
to `""` still persists. Covered by `ApplicationItemAliasTest` (typing, focus
loss, clear-to-empty, untouched close) on `emulator-5556`; the dev APK is
installed and launches without fatal exceptions.

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
