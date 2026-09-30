# Context-Aware Home App Selection

## Goal

Add an opt-in, on-device learned ranking for the Home screen's top apps. Keep the
current time/day ranking available and make the two strategies mutually
selectable from Settings. The learned strategy uses launch history, local time,
location, and Wi-Fi context; it must not upload history or model data.

## Decisions and assumptions

- Settings will offer **Existing ranking** and **Learned ranking**; existing
  ranking remains the default.
- Home is a recommendation shortlist, not the only way to launch an app. Rare
  apps may be omitted from recommendations because the full application list
  remains available. Rare-label coverage is diagnostic, not a model-selection
  gate.
- Learned mode requires the user to separately enable both location logging
  and Wi-Fi logging and grant the corresponding location permission; choosing
  the mode will not silently enable data collection. If a required prerequisite
  is disabled or a usable current location is temporarily unavailable, use the
  existing ranking. No active Wi-Fi connection is not a failure: it is a
  `NO_WIFI` context when that state is known.
- Do not treat old/unrecorded Wi-Fi values as `NO_WIFI`.
- The learner uses launch events Neurhome records. It does not observe launches
  made outside Neurhome; observing those would require a separate Usage Access
  permission and scope decision.
- No Android ML runtime is proposed. The candidate is a compact pairwise model
  trained from local launch history; production use depends on the offline
  frequent-label gate.

## Current implementation and data

- The live Home ranking calls `ApplicationLogEntryDao.topAppsByScore()` through
  `NeurhomeRepository.getTopApps(6)`. It considers the last four months, a
  20-minute time-of-day window, weekday, and workday/weekend patterns. It does
  not use location or Wi-Fi. The simulated ranking accepts Wi-Fi but not
  location and is used by app statistics, not the live Home ranking.
- The user reports having roughly six years of launch history. The live query's
  four-month filter is not evidence that older rows are unavailable; inspect an
  export before choosing the learned model's training horizon.
- Read-only aggregate audit of `neurhome_database_sample.db` (originally Room
  version 19):
  106,084 launches span 2020-04-08 through 2026-09-30, with 337 package names
  and 348 package/profile pairs. Coordinates are present on 106,005 rows
  (99.9%), Wi-Fi on 64,153 (60.5%), and geohash on 85,402 (80.5%). There are
  994 distinct five-character geohash cells and 153 distinct non-null Wi-Fi
  values. In particular, 41,916 rows have coordinates but no Wi-Fi value, so
  null Wi-Fi must not be assumed to mean disconnected.
- The sample has a long app tail: 166 of 348 app/profile pairs have fewer than
  10 launches, while the 10 most-used pairs account for 71.8% of launches.
  Exact app/profile + coarse-cell + weekday + hour + Wi-Fi groupings are sparse
  (31,149 groups, of which 3,217 have at least five events). A learned model
  should share statistical strength across contexts, regularize rare app
  labels, and be compared to the current ranking on later, held-out history.
- `ApplicationLogEntry` (database version 19) records:
  - `packageName`, `user` (Android profile), and `timestamp`;
  - nullable `wifi`, `latitude`, `longitude`, and `geohash` (current source
    writes 9-character hashes; the sample has both 9- and 12-character hashes);
  - optional `query` for launches from search.
- Task 2 is complete and committed as `19dfaf5`; the app database is now Room
  version 20 with explicit `wifiState`. Historical v19 null Wi-Fi remains
  `UNKNOWN`. The offline evaluator accepts v19 and v20 exports; `NO_WIFI` must
  not be inferred from the old export.
- Weekday and time-of-day can be derived from `timestamp`; the schema does not
  store explicit day/weekend columns. App identity should continue to include
  package and Android profile.
- `Setting` is a string key/value table. `SettingsRepository` already stores
  the Wi-Fi and position logging choices there, so the selected ranking can
  also be stored there without a schema change.
- Wi-Fi logging is enabled/disabled by `NeurhomeApplication`; launch logging
  reads the current SSID. The position setting is shown in Settings, but is
  not currently consulted by the position capture path. `getPosition()` only
  reads the last-known fused location, which may be missing or stale.
- Nullable `wifi` currently conflates “not connected”, “not collected”, and
  “not yet known”. The learner needs to distinguish these cases. Existing
  database rows must remain `UNKNOWN`, not be reinterpreted as `NO_WIFI`.

## Proposed design

### 1. Selection setting and permissions

- Add a persisted setting such as `home.app.selection`, with values
  `CLASSIC` (default) and `LEARNED`.
- Add a two-choice control to `SettingsScreen` and expose the selected mode in
  `SettingsViewModel`/`SettingsRepository`.
- Explain that Learned mode needs both existing logging options and location
  permission. Prevent selecting it until these requirements are met, or guide
  the user to enable them. If a requirement is later disabled/revoked, keep the
  preference but temporarily show the existing ranking with a clear status.
- Make `log.position` actually gate location capture. Do not add background
  location collection; use a current/recent foreground location for ranking
  and for recorded launches. Reject stale fixes and use the existing ranking
  when a usable coordinate is unavailable.

### 2. Context capture and schema

- Keep the existing launch event as the training example: context at launch is
  the input and the opened package/profile is the target.
- Add an explicit Wi-Fi context state, for example `CONNECTED`, `NO_WIFI`, or
  `UNKNOWN`. `CONNECTED` uses the existing `wifi` SSID; `NO_WIFI` is recorded
  only when collection is enabled and the device is known not to be on Wi-Fi;
  disabled, permission-blocked, and not-yet-initialized capture remains
  `UNKNOWN`.
- Because old rows cannot be classified reliably, add the field with an
  `UNKNOWN` migration default, bump Room to version 20, add the auto-migration,
  and commit the generated schema. No other model table is needed for the
  initial version; the event log itself is the local training history.
- Keep precise coordinates in the existing event record as today, but use a
  coarsened geohash/location band in ranking so minor GPS drift does not create
  a new place and exact coordinates are not surfaced by the model UI/logs.
- Never send coordinates, SSIDs, launch history, or model artifacts over the
  network.

### 3. Local learning and ranking

- Keep the current SQL ranking in production until the selected candidate is
  verified in its Kotlin implementation. Initial linear/neural classifiers
  failed to improve ranking quality. The follow-up is a trainable pairwise
  residual reranker over classic SQL candidates; it learns context-conditioned
  preferences instead of using hand-set similarity weights.
- Each launch supplies its context as input and package/profile as the target.
  Encode local time cyclically, day of week, coarsened location, and Wi-Fi
  state/SSID. The pairwise reranker may reorder only positive-score classic SQL
  candidates with at least 10 prior launches; lower-support apps may be omitted
  from Home recommendations but remain accessible in the full application
  list. Rank package/profile pairs separately.
- The offline sweep automatically tunes horizon, candidate count, epochs,
  learning rate, and regularization using fold 1 only. Its selected candidate
  passed the frequent-label HitRate@6/MRR guard on fold 1 and fold 2. The
  isolated Kotlin model and historical example factory are implemented;
  rerunning the same evaluation against that implementation is required before
  Home integration. All-label and rare-label metrics are diagnostics.
- For on-device use, train or update locally from Room history after new launch
  events, using replay or periodic retraining to avoid overreacting to one
  launch. Keep a versioned local checkpoint if needed; no model, history, SSID,
  or location leaves the device. Audit full-history and shorter/decayed horizons
  through the automated sweep before choosing one.
- Unknown historical context contributes no Wi-Fi/location evidence; it must
  not be treated as `NO_WIFI`. If there is not enough history or the model is
  unavailable, use the existing ranking.
- Preserve current behavior for apps hidden from Top, unavailable launcher
  activities, quiet profiles, and the six-item Home limit. Keep the existing
  implementation as a separate strategy so it remains a straightforward
  fallback and comparison baseline.
- Recompute on the existing Home refresh cadence initially. If query cost grows,
  move scoring off the UI thread and cache/invalidate on new log events rather
  than prematurely introducing a heavyweight ML runtime.

## Delivery steps

1. Audit the exported history and benchmark candidate learned models against
   the current ranking with chronological holdouts. Initial classifiers did not
   pass; the pairwise reranker has an offline candidate selected. Verify its
   Kotlin implementation against the same frequent-label gate before
   production selection.
2. Add explicit Wi-Fi context state and make location capture respect its
   setting; migrate historical Wi-Fi state as `UNKNOWN`.
3. Complete implementation-parity evaluation of the selected Kotlin model
   against the passing offline candidate, then implement/test the local
   retraining lifecycle. Rare-app ranking is diagnostic, not a release
   requirement; all apps remain launchable outside the recommendations.
4. Add the Settings selector, prerequisites/status messaging, and persisted
   mode selection.
5. Integrate model predictions into the Home ranking flow with fallback and
   existing filters intact.
6. Verify migration, model behavior, settings, and both modes on device.

## Acceptance criteria

- Existing ranking is unchanged when selected and remains the first-run
  default.
- Learned ranking responds to recorded launches and uses time, location, and
  connected-SSID versus known-no-Wi-Fi context; it is computed locally.
- Missing/uncollected historical context is not mistaken for a no-Wi-Fi
  observation. A missing current location or unavailable prerequisites do not
  leave Home without suggestions; the existing ranking is used instead.
- A learned recommendation list may omit rare apps; every installed app remains
  accessible from the full application list. Model release selection is gated by
  frequent-label HitRate@6 and MRR against the frequent-only classic baseline,
  not rare-label coverage.
- The user can switch modes in Settings, and the selection survives process
  restarts and database migration.
- Location capture obeys the location logging setting and runtime permission;
  no background location permission or new Usage Access permission is added.
- Existing exclusions, profile handling, Home refresh behavior, and
  application launch behavior remain intact.
- Unit tests cover model and feature-encoding edge cases; Room migration and
  Settings selection are tested; `:app:assembleDevDebug` succeeds and the dev
  APK is smoke-tested on a device when available.

## Confirmed scope

The learning history is limited to launches recorded when the user opens apps
through Neurhome. No Usage Access permission or tracking of launches from other
apps/launchers is needed.

## Implementation tasks

See the [individual implementation tasks](context-aware-home-app-selection/tasks/README.md)
for sequenced task scopes, dependencies, and acceptance criteria.
