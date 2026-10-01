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

## Status: complete

Implemented in the `home.app.selection` key of the existing `Setting` table, so no
Room migration was needed.

- `HomeAppSelection` (`CLASSIC`, `LEARNED`) and `fromStoredValue` live in
  `data/repositories/SettingsRepository.kt`; an absent or unrecognized stored
  value parses to `CLASSIC`.
- `SettingsRepository` exposes `homeAppSelection: Flow<HomeAppSelection>` and
  `setHomeAppSelection(...)`. Its coroutine scope is now constructor-injected and
  defaults to `Dispatchers.IO` (previously `Dispatchers.Main` with the write
  dispatched to IO), which keeps writes off the main thread and makes the
  repository unit-testable on the JVM.
- `SettingsViewModel`/`ISettingsViewModel` expose `Settings.homeAppSelection` and
  `setHomeAppSelection(...)`, folded into the existing `combine` chain.
- `LearnedRankingPrerequisites` (`ui/settings`) computes the requirements and the
  user-facing explanation. Wi-Fi connectivity is intentionally not a requirement,
  so a known `NO_WIFI` state stays valid input.
- `SettingsScreen` renders `HomeAppSelectionSelector`, a stateless composable with
  radio buttons plus a prerequisite caption. The location permission is requested
  only when the user explicitly picks Learned and it is not granted; picking
  Learned never enables Wi-Fi or position logging, and picking Classic never
  requests anything. Revoked prerequisites keep the stored selection and only add
  the "inactive, Home keeps using the classic ranking" caption.

### Verification

- `./gradlew :app:testDevDebugUnitTest` — 37 tests, 0 failures, 1 skipped (the
  fixture-gated `PairwiseHomeAppChronologicalParityTest`; `HOME_APP_RERANKER_PARITY_FIXTURE`
  intentionally unset).
- `./gradlew :app:assembleDevDebug` and `:app:assembleDevDebugAndroidTest` — both
  build.
- `python3 -m unittest discover -s tools/tests` — 13 tests, OK.
- `HomeAppSelectionSelectorTest` (androidTest) compiles and packages but was not
  executed: no device was attached (`adb devices` empty), so
  `:app:connectedDevDebugAndroidTest` still needs a run on hardware.
