# Task 6: End-to-End Verification

## Objective

Verify migration, permission behavior, online learning, and both Home ranking
modes on supported Android devices.

## Scope

- Run Room migration tests from the v19 schema and unit tests for feature
  encoding, model training/inference, and fallback behavior.
- Run the historical chronological evaluation and compare the final model to
  the classic baseline using the same splits and metrics.
- Verify Settings selection, enabling/disabling each logger, permission grant
  and revocation, active Wi-Fi, known no-Wi-Fi, and unavailable location.
- Confirm model updates remain local and launch prediction stays responsive.
- Verify that apps omitted from learned recommendations remain available and
  launchable through the full application list; rare-label recommendation
  coverage is diagnostic, not a release gate.
- Build and, when a device is available, deploy/smoke-test the dev debug APK.

## Acceptance criteria

- `./gradlew :app:testDevDebugUnitTest` and `./gradlew :app:assembleDevDebug`
  succeed; connected tests run when a device/emulator is available.
- A v19 database migrates without data loss; older Wi-Fi-null records remain
  unknown.
- Both ranking modes work, and learned mode handles permission loss or missing
  current location by falling back to classic ranking. Frequent-label
  HitRate@6/MRR meet the approved chronological gate; rare-label metrics are
  reported but do not block release.
- The local sample database and any unredacted export are not included in the
  APK, test fixtures, generated reports, or source control.

## Dependencies and verification

- Depends on Tasks 1–5, including Task 3's implementation-parity verification.
  Follow `AGENTS.md` deployment guidance after a successful dev build when a
  device is attached.
