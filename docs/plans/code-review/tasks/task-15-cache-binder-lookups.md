# Task 15: Cache Profiles and Quiet-Mode Binder Lookups

**Status: Done.** `ApplicationService` now caches the launcher profile set and each profile's quiet mode in one read-through value (`BinderLookupCache<LauncherProfileState>`), so the 30 s refresh reads them without a binder call; `NeurhomeRepository.listAllLauncherApps.refresh()` invalidates that cache from the `LauncherApps` package callbacks, so work-profile add/remove and quiet-mode toggling are re-read on the next lookup.

Source: Code review — Performance 7. Priority: P2.

## Problem

`getTopAppsOnce` calls `applicationService.toApplication`
(PackageManager/LauncherApps) for candidates every 30 s, and `quietModes` does
per-profile `isQuietModeEnabled` binder calls each tick.

## Scope

- Cache profile/quiet-mode state and update it on change (e.g. `LauncherApps`
  callback), rather than querying every tick.
- Keep the 30 s refresh cheap.

## Acceptance criteria

- Steady-state 30 s refresh does not perform per-profile binder calls.
- Work-profile add/remove and quiet-mode changes are still reflected.
- `./gradlew :app:assembleDevDebug` succeeds.

## Dependencies

Related to Task 22 (profile staleness / `ApplicationService.profiles`).
