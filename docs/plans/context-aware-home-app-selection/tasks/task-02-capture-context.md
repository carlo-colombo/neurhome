# Task 2: Capture Explicit Context

**Status: Complete.** Committed as `19dfaf5` (`Capture explicit launch
context`); Room is version 20. See the Task 3 handoff for how the offline
evaluator handles v19/v20 Wi-Fi state.

## Objective

Record enough context on each Neurhome launch to distinguish connected Wi-Fi,
known no-Wi-Fi, and unavailable context without misclassifying historical rows.

## Scope

- Add a persisted Wi-Fi context state such as `CONNECTED`, `NO_WIFI`, and
  `UNKNOWN`; retain the SSID only for `CONNECTED`.
- Mark `UNKNOWN` when logging is disabled, permission/context is unavailable, or
  the network state has not initialized. Mark `NO_WIFI` only when Wi-Fi logging
  is enabled and disconnection is known.
- Bump Room from version 19 to 20, add the auto-migration with an `UNKNOWN`
  default for old records, and commit the generated schema. Do not rewrite
  historical null SSIDs as `NO_WIFI`.
- Make the existing `log.position` setting gate location capture, and reject
  stale/unusable location fixes for current-context prediction. Do not add
  background location collection.
- Keep exact launch coordinates local as the existing schema does; expose only
  coarsened location features to the model.

## Acceptance criteria

- Migrated v19 rows have unknown Wi-Fi state and retain their existing values.
- Connected, known-disconnected, and unknown cases are separately testable.
- Location is not captured when its setting or permission is off, and no
  background-location permission is introduced.
- The latest Room schema JSON is generated and committed; historical schemas
  remain unchanged.

## Dependencies and verification

- Can begin after Task 1's feature definitions; model training depends on this
  task's context semantics.
- Test migration from a v19 fixture and test context-state classification.
