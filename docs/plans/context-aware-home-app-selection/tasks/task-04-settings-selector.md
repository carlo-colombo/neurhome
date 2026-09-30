# Task 4: Add the Settings Selector

## Objective

Let the user select the existing or learned Home ranking from Settings.

## Scope

- Add a persisted `home.app.selection` setting with `CLASSIC` as the default.
- Add a clear two-choice selector to `SettingsScreen` and expose it through
  `SettingsRepository` and `SettingsViewModel`.
- Keep location and Wi-Fi logging as separate privacy opt-ins; selecting the
  learned mode must not silently enable either logger.
- Require both logging options and the location permission for learned mode.
  Explain missing prerequisites; if they are later disabled/revoked, retain the
  preference but allow Home to fall back to the classic ranking.
- Do not require an active Wi-Fi connection. Known `NO_WIFI` is valid input.

## Acceptance criteria

- Classic is selected for existing installs and clean installs unless the user
  chooses otherwise.
- The setting survives process restarts and uses the existing `Setting`
  key/value table without requiring a database migration for the selection.
- Permission/logger revocation has clear UI feedback and does not crash Settings
  or Home.

## Dependencies and verification

- Start only after Task 3's Kotlin implementation passes chronological parity
  verification. Do not expose a Learned option while no production model is
  verified; integration with Home depends on Task 5.
- Add repository and Compose tests for selection persistence and prerequisites.
