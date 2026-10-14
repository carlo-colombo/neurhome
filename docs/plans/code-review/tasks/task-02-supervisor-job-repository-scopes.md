# Task 2: Add `SupervisorJob` to Repository Coroutine Scopes

**Status: Done.** Both repositories now use `SupervisorJob() + Dispatchers.Main`;
blocking writes already run on explicit `Dispatchers.IO`, so the dispatcher choice
is safe.

Source: Code review — Bugs 2. Priority: P0.

## Problem

`NeurhomeRepository` (`NeurhomeRepository.kt:92`) and `FavouritesRepository`
(`FavouritesRepository.kt:16`) use `CoroutineScope(Dispatchers.Main)` with no
`SupervisorJob`. One uncaught exception permanently cancels the scope, so all
later writes silently stop. `SettingsRepository` already uses
`SupervisorJob() + Dispatchers.IO`.

## Scope

- Change both repositories to `CoroutineScope(SupervisorJob() + <dispatcher>)`.
- Choose dispatchers consistently with `SettingsRepository` (IO for writes).
- Add a short comment explaining why the supervisor job matters.

## Acceptance criteria

- A failing write does not cancel subsequent writes.
- Scope creation matches `SettingsRepository`'s pattern.
- `./gradlew :app:testDevDebugUnitTest` and `:app:assembleDevDebug` succeed.

## Dependencies

None.
