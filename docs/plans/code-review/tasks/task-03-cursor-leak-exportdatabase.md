# Task 3: Fix Cursor Leak in `exportDatabase`

**Status: Done.** The `pragma wal_checkpoint` cursor is closed via `.use { ... }`,
and the provider is injected as `(Context) -> Uri`
(`NeurhomeFileProvider()::getDatabaseURI`) instead of being constructed inline.

Source: Code review — Bugs 3. Priority: P1.

## Problem

`NeurhomeRepository.kt:414` obtains a cursor for
`pragma wal_checkpoint(full)` and never closes it. The same area instantiates
`NeurhomeFileProvider()` manually instead of injecting it.

## Scope

- Wrap the `pragma` cursor in `.use { ... }`.
- Inject `NeurhomeFileProvider` (or a small file-export dependency) instead of
  constructing it inline.

## Acceptance criteria

- No unclosed `Cursor` remains in `exportDatabase`.
- Database export still produces a valid file (manual smoke or existing test).
- `./gradlew :app:assembleDevDebug` succeeds.

## Dependencies

None.
