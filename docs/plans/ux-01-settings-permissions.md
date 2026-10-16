# UX-01 — Settings & Permission Usability Plan

Read-only design artifact. Verified against the current working tree (HEAD `5a452f4`). Every
claim cites `file:line`. Items I could not confirm from source are explicitly marked
**UNVERIFIED**.

Scope anchors:

- `app/src/main/java/ovh/litapp/neurhome3/ui/settings/SettingsScreen.kt` (334 lines total)
- `app/src/main/java/ovh/litapp/neurhome3/ui/settings/SettingsViewModel.kt`
- `app/src/main/java/ovh/litapp/neurhome3/ui/settings/LearnedRankingPrerequisites.kt`
- `app/src/main/java/ovh/litapp/neurhome3/ui/settings/LargeDropdownMenu.kt`
- `app/src/main/java/ovh/litapp/neurhome3/data/repositories/SettingsRepository.kt`
- `app/src/main/java/ovh/litapp/neurhome3/data/dao/SettingDao.kt`
- `app/src/main/java/ovh/litapp/neurhome3/application/LogSSID.kt` (BUG-06)
- `app/src/main/java/ovh/litapp/neurhome3/MainActivity.kt` (UX-02)
- `app/src/androidTest/.../ui/settings/HomeAppSelectionSelectorTest.kt` (regression surface)
- `docs/backlog.md` (UX-01 detail, lines 203-214)

Settings destination is reached from the application list via the `Settings` icon
(`AllApplicationsScreen.kt:31-34`) and is wired in the NavHost at `MainActivity.kt:78-88`.

---

## A. Inventory of every settings row

Rendered top-to-bottom by `SettingsScreen.kt:62-125`. "Permission-backed" = uses
`rememberPermissionState`.

| # | Row / control | Controls | Permission-backed | Permission | How request is launched | Stored flag can be `true` while permission denied? |
|---|---|---|---|---|---|---|
| 1 | `LogWiFi` "Log Wi-Fi access point name" (`SettingsScreen.kt:262-270`) | persisted `log.wifi` → toggles Wi-Fi/SSID logging collector (`SettingsRepository.kt:30-31`, collector at `NeurhomeApplication.kt:157-166`) | Yes | `ACCESS_FINE_LOCATION` (`SettingsScreen.kt:264-267`) | `SettingWithPermission`: checkbox click (`:291-293`) **and** `SideEffect` whenever `state && !granted` (`:279-282`) | **Yes** — BUG-07. `toggle()` runs unconditionally (`:294`) and flips the DB row to "true" (`SettingDao.kt:38-46`) regardless of grant result |
| 2 | `LogPosition` "Log position" (`SettingsScreen.kt:251-260`) | persisted `log.position` → position collector (`SettingsRepository.kt:33-34`, `NeurhomeApplication.kt:167-171`) | Yes | `ACCESS_FINE_LOCATION` (`SettingsScreen.kt:253-256`) | same `SettingWithPermission` path | **Yes** — same code path |
| 3 | `HomeAppRankingSection` "Home app ranking" (`SettingsScreen.kt:132-147`) → `HomeAppSelectionSelector` (`:154-208`) | persisted `home.app.selection` (`SettingsRepository.kt:53-58`), Classic vs Learned | Yes (only to gate Learned) | `ACCESS_FINE_LOCATION` (`:136`) | On explicit "Learned" tap only (`:176-179`); no SideEffect | Stored value can be `LEARNED` while prerequisites unmet — **by design**, explained by `LearnedRankingPrerequisites.explain` (`LearnedRankingPrerequisites.kt:33-43`). Permission specifically: `locationPermissionGranted` is recomputed from the live grant each composition, so a revoked grant never leaves stale state |
| 4 | `ShowCalendar` "Show Calendar" (`SettingsScreen.kt:231-239`) | persisted `show.calendar` (`SettingsRepository.kt:36-37`) | Yes | `READ_CALENDAR` (`:233-236`) | `SettingWithPermission` | **Yes** — same code path |
| 5 | `ShowStarredContacts` "Show Starred Contacts" (`SettingsScreen.kt:241-249`) | persisted `show.contacts.starred` (`SettingsRepository.kt:39-40`) | Yes | `READ_CONTACTS` (`:243-246`) | `SettingWithPermission` | **Yes** — same code path |
| 6 | "Show alternative time" (`SettingsScreen.kt:78-81`) | persisted `show.alternative.time` (`SettingsRepository.kt:42-43`) | No | — | Plain `Checkbox` (`:79`) | N/A |
| 7 | "Time zone" `LargeDropdownMenu` (`SettingsScreen.kt:87-95`) | persisted `alternative.time.zone` (`SettingsRepository.kt:45-46`) | No | — | Dropdown selection (`:91-94`) | N/A |
| 8 | "Export database" (`SettingsScreen.kt:98`, `:209-224`) | action: WAL checkpoint + `ACTION_SEND` chooser (`NeurhomeRepository.kt:485-505`) | No | — | `IconButton` (`:220-222`) | N/A (no stored flag) |
| 9 | "Replace database" (`SettingsScreen.kt:99-113`) | action: `OpenDocument` picker → `NeurhomeApplication.replaceDatabase` → unconditional `restart()` (`MainActivity.kt:78-88`, `:100-107`) | No | — | `rememberLauncherForActivityResult(OpenDocument)` (`:104-107`) | N/A |
| 10 | "Get latest release" (`SettingsScreen.kt:114-125`) | action: open release URL | No | — | `Button` (`:122`) | N/A |

Notes:

- `SettingWithPermission` creates one `rememberPermissionState` **per row** (`:276-278`); rows 1, 2,
  4, 5 each hold an independent state object, so the same `ACCESS_FINE_LOCATION` permission has two
  separate states (rows 1 and 2), and the selector holds a third (`:136`).
- The checkbox visual is `state && permission.status.isGranted` (`:290`), but the stored value is
  `state` alone. When they disagree the UI silently shows "off" while the DB says "on".
- No settings row currently opens the system app-settings screen; there is no
  `ACTION_APPLICATION_DETAILS_SETTINGS` usage anywhere in `app/src/main` (grep: only
  `ApplicationItem.kt`, `Weather.kt`, `Sun.kt`, `Calendar.kt`, `ApplicationList.kt`,
  `HomeApplicationsList.kt`, `SettingsScreen.kt` reference `rememberPermissionState`/`isGranted`).

---

## B. Concrete defect list

| ID | Defect | Evidence | Status |
|---|---|---|---|
| BUG-07a | A permission-backed toggle persists "on" even when the grant is denied/never granted. `onCheckedChange` calls `toggle()` unconditionally after launching the request (`SettingsScreen.kt:290-295`); `toggle()` flips the DB row to "true" (`SettingDao.kt:38-46`). | `SettingsScreen.kt:290-295`, `SettingDao.kt:38-46` | **verified** |
| BUG-07b | Re-prompt loop: `if (state && !permission.status.isGranted) { SideEffect { launchPermissionRequest() } }` fires a permission request after **every** recomposition for as long as the flag is on and the permission is not granted (`SettingsScreen.kt:279-282`). Combined with BUG-07a the flag stays true after a denial, so the request is re-issued continuously. | `SettingsScreen.kt:279-282` | **verified** |
| BUG-07c | Checkbox shows unchecked while the stored value is true (`checked = state && permission.status.isGranted`, `:290`), so the user cannot tell that the setting is actually persisted on; unchecking calls `toggle()` which will then delete the row (`:294` + `SettingDao.kt:43-45`). State desync is user-visible. | `SettingsScreen.kt:290`, `SettingDao.kt:43-45` | **verified** |
| BUG-06 | `enableSSIDLogging` sets `wifiLoggingEnabled(true)` **before** `registerNetworkCallback`; on `SecurityException` it returns with the flag still true and `networkCallback == null`, so logging state reports enabled while no callback is registered and no context is collected (`LogSSID.kt:22-41`). Settings reads the persisted flag, not the runtime state, so the UI also reports "on". | `LogSSID.kt:22-41`, `ApplicationLoggingState.kt:33-34` | **verified** |
| REF-08 | `zones`, `selectedZone`, `selectedIndex` are plain `var`s re-created inside the composable body, so the 600+ entry `ZoneId` list is re-sorted on every recomposition (`SettingsScreen.kt:83-85`). Also `selectedZone` is written on selection (`:92`) but is not observable state, so the dropdown's `selectedIndex` only updates once `uiState.alternativeTimeZone` re-emits. `LargeDropdownMenu` re-runs `scrollToItem` when `selectedIndex > -1` (`LargeDropdownMenu.kt:105-108`). | `SettingsScreen.kt:83-95`, `LargeDropdownMenu.kt:105-108` | **verified** |
| UX-02 | Database replacement has no failure surface and restarts unconditionally. `replaceDatabase` calls `contentResolver.openInputStream(u)?.use { ... }` — a `null` stream (or a `copyTo` failure) is silently ignored — then always calls `rebindDataLayer()` (`NeurhomeApplication.kt:172-190`). `MainActivity` always calls `restart()` after `replaceDatabase` returns (`MainActivity.kt:78-88`), with no path to show an error or retry. | `NeurhomeApplication.kt:172-190`, `MainActivity.kt:78-88`, `:100-107` | **verified** |
| UX-02b | `restart()` uses `FLAG_ACTIVITY_CLEAR_TASK` + `finish()` (`MainActivity.kt:100-107`); if the copy failed, the app has already had its DB closed, so the relaunched process starts against a same-named file that may not have been overwritten. There is no distinction between "no file chosen", "copy failed", and "success". | `MainActivity.kt:100-107` | **verified** |
| UX-01-extra | Settings rows have no failure/feedback surface at all. `exportDatabase` launches an `ACTION_SEND` chooser from a background coroutine (`NeurhomeRepository.kt:485-505`) and `LaunchedEffect`/error handling is absent; `replaceDatabase` (above) is likewise fire-and-forget. Backlog UX-01 detail also lists "restore/export surfaced without failure handling → UX-02" but labels it `BUG-16`, while `docs/backlog.md:54` defines `BUG-16` as PII logging. The ID is inconsistent in the doc; treat the export/restore failure item as part of UX-02. | `docs/backlog.md:54`, `:207`, `:223` | **verified** (doc inconsistency) |
| UX-01-a11y | Permission rows are not screen-reader friendly and have small touch targets. The label `Text` is not clickable and the whole `Row` (`SettingsScreen.kt:284-296`) has no `toggleable`/`selectable` semantics, so only the `Checkbox` itself (`:290`) toggles. There is no merged semantics, no state description, and no rationale/helper text. | `SettingsScreen.kt:284-296` | **verified** |
| UX-01-zone-label | The dropdown field can display an empty value: `alternative.time.zone` defaults to `""` (`SettingsRepository.kt:45-46`, `SettingsViewModel.kt:95`), `zones.indexOf("")` returns `-1`, and `LargeDropdownMenu` renders `""` when `selectedIndex <= -1` (`LargeDropdownMenu.kt:70-73`). The "Time zone" field looks blank with no placeholder. | `SettingsRepository.kt:45-46`, `LargeDropdownMenu.kt:70-73` | **verified** |
| UX-01-dup-permission | Three independent `rememberPermissionState(ACCESS_FINE_LOCATION)` instances on one screen (`SettingsScreen.kt:136`, and rows 1 & 2 via `:276`). Each holds its own launcher; a grant obtained through one is not observed by the others until recomposition. Related to `docs/plans/permission-state.md` (that plan is about `ApplicationItem`/`CALL_PHONE`, not Settings). | `SettingsScreen.kt:136`, `:276-278`, `docs/plans/permission-state.md` | **verified** |
| UX-01-doc | `docs/plans/permission-state.md` addresses `CALL_PHONE` per-row state in `ApplicationItem`, not the settings permission rows. It is the referenced "permission-state helper" doc but does not cover UX-01's settings surface. | `docs/plans/permission-state.md:1-20` | **verified** |

**UNVERIFIED items**

- Whether repeated `launchPermissionRequest()` calls on Android 11+ actually re-show a dialog when
  the permission is permanently denied (system usually suppresses it) vs. merely re-post the
  launcher. The code-level re-prompt on every recomposition is verified; the exact OEM behavior is
  **UNVERIFIED** (not testable here).
- Whether `Settings` state is collected with a lifecycle-aware operator. `SettingsScreen` uses
  `viewModel.uiState.collectAsState()` (`SettingsScreen.kt:50`) — the backlog separately lists
  lifecycle-unaware `collectAsState()` as PERF-13/REF-06; UI-01 relevance is **UNVERIFIED**.

---

## C. Proposed interaction model

Goal: explicit, request-once, never store a granted-only setting without the grant, and never
silently re-prompt.

1. **Request-once semantics.** Delete the `SideEffect` block (`SettingsScreen.kt:279-282`)
   entirely. A permission request may only originate from an explicit user action (the checkbox
   tap), matching the already-correct selector path (`:176-179`). No composable may launch a
   request as a side effect of rendering. If a rationale must be shown on entry, compute it from
   the live `permission.status` without launching anything.

2. **Do-not-store-without-grant.** Restructure the toggle so the persisted write is conditional on
   the grant, not on the click:
   - Unchecking (grant already exists or not) → persist `false` immediately. This is always safe.
   - Checking when not granted → launch the request and **remember a pending intent**
     (`remember { mutableStateOf(false) }`); persist `true` only when the grant is observed.
   - Checking when already granted → persist `true` immediately.
   This requires moving the "persist on grant" observation into the row (or into the ViewModel) and
   away from the current unconditional `toggle()`. Note `SettingsRepository` only exposes a
   `toggle` (`SettingsRepository.kt:31,34,37,40`); a non-toggle setter (`setWifiLogging(Boolean)`)
   will be needed to make the write idempotent and grant-gated. `toggleSetting` cannot distinguish
   "turn on" from "turn off" (`SettingDao.kt:38-46`).

3. **Disabled-but-explained vs checked-but-ignored.** Recommend **disabled-but-explained** for the
   four grant-only rows: render the switch as off + disabled until the grant exists, with a helper
   line ("Needs Location access") and an inline "Grant" affordance. Rationale: the persisted value
   is meaningful only when the grant exists, and the current "checked-but-ignored" look
   (`checked = state && isGranted`) is exactly what desynchronizes (BUG-07c). Exception: the
   `HomeAppSelectionSelector` should stay **checked-but-explained**, because its LEARNED choice is
   intentionally stored before prerequisites are met (`LearnedRankingPrerequisites.kt:33-43`) and
   its strings are asserted by tests (see E). Do not change the selector semantics.

4. **Permanently-denied handling → open app settings.** When the user checks a row and the system
   no longer shows a dialog (i.e. the grant did not change after the request settles), show a
   message and a button that opens the app details screen via
   `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())`. There is
   no such intent anywhere today (grep of `app/src/main`). This is new surface; it must not alter
   the four `testTag`s.

5. **Avoid the recomposition-driven re-prompt.** Two defenses, either is sufficient:
   - Remove `SideEffect` (primary fix).
   - Additionally key any post-request observation with `LaunchedEffect` on the *request token*
     (an incrementing counter bumped only by a click), not on `state`, so a render can never
     trigger it. `LaunchedEffect` keyed on `permission.status.isGranted` is the idiom already used
     by `Calendar.kt:61-65`, `Sun.kt:40-44`, `Weather.kt:44-48`; align Settings with those.
   - Optionally consolidate the three `ACCESS_FINE_LOCATION` states into one shared state passed
     into the rows (echoing `docs/plans/permission-state.md`'s "create once per parent" principle)
     so a grant is observed consistently by rows 1, 2, and 3.

6. **Persistence-layer guard (defense in depth).** Have the logging collectors treat "flag true but
   `canReadWifiContext()`/location permission absent" as off (`LogSSID.kt:57-64` already gates
   reads this way). This complements BUG-06/07 so a stale DB flag cannot cause collection without a
   grant. Note this does not replace the UI fix.

---

## D. Information architecture

Current order (`SettingsScreen.kt:62-66`, `:67`, `:98-125`) mixes logging, display, and maintenance
with no grouping or headers. Proposed grouping and order:

1. **Logging & privacy** (permission-backed, collect data)
   - Log position
   - Log Wi-Fi access point name
   - Each with helper text explaining the data captured and the required permission.
2. **Home** (ranking)
   - Home app ranking (Classic / Learned), with the existing prerequisite explanation.
3. **Home widgets** (display-only, permission-backed read)
   - Show Calendar
   - Show Starred Contacts
4. **Time**
   - Show alternative time
   - Time zone (nested/indented under the toggle; show a placeholder when unset)
5. **Data & maintenance** (destructive/actions, visually separated)
   - Export database
   - Replace database (with a destructive/confirmation affordance)
6. **About**
   - Get latest release

Labels & helper text:

- Keep row text exactly as-is where tests assert it (see E). Add helper text as **separate**
  `Text` nodes, not by editing the primary labels.
- Permission rows: helper "Requires Location / Calendar / Contacts access" plus state-specific
  text ("On — logging your Wi-Fi name" vs "Off").
- `Show alternative time` / `Time zone`: give the dropdown `notSetLabel = "Select a time zone"` so
  the empty-default case (`SettingsRepository.kt:45-46`) reads sensibly; `LargeDropdownMenu` already
  supports `notSetLabel` (`LargeDropdownMenu.kt:79-88`).
- `Replace database`: helper "Overwrites all current data. The app restarts afterwards."

Touch targets and semantics:

- Make the whole `Row` a single toggle target with `Modifier.toggleable(value, role = Role.Switch,
  onValueChange = ...)` (or `Role.Checkbox`) and merge descendants so the label and state announce
  together. Today only the `Checkbox` is clickable (`SettingsScreen.kt:290`).
- Ensure each row is ≥ 48dp. Add `Modifier.heightIn(min = 48.dp)` to the row container.
- Apply the same `toggleable` treatment to "Show alternative time" (`:78-81`), whose checkbox is
  likewise the only target.
- Add `stateDescription` for the permission rows ("On, location granted" / "Off, needs location").
- Keep `Icon` `contentDescription`s unchanged ("Replace database" `:110`, "Export Database" `:222`);
  the app-list "Settings" content description is outside this screen
  (`AllApplicationsScreen.kt:36`).
- Disabled rows must still be focusable and read the reason; do not simply drop them from the tree.

---

## E. Regression risk — tags and semantics to preserve

`MainActivity.kt:46` sets `testTagsAsResourceId = true` at the root, so **every** `testTag` on this
screen is also a UiAutomator/Maestro `resource-id`. Settings-screen tags currently in use:

| Constant | Value | Declared | Used by |
|---|---|---|---|
| `HomeAppSelectionSelectorTestTag` | `home-app-selection-selector` | `SettingsScreen.kt:44`, applied `:167` | `HomeAppSelectionSelectorTest.kt:138` |
| `HomeAppSelectionClassicTestTag` | `home-app-selection-classic` | `:45`, applied `:172` | `HomeAppSelectionSelectorTest.kt:39,48,95` |
| `HomeAppSelectionLearnedTestTag` | `home-app-selection-learned` | `:46`, applied `:178` | `HomeAppSelectionSelectorTest.kt:40,47,62,66,79` |
| `HomeAppSelectionPrerequisiteTestTag` | `home-app-selection-prerequisite` | `:47`, applied `:189` (in the `explanation != null` block starting `:186`) | `HomeAppSelectionSelectorTest.kt:112,126,139` |

Semantics the tests rely on (must not change):

- `Role.RadioButton` via `Modifier.selectable(...)` (`SettingsScreen.kt:196`), asserted with
  `assertIsSelected()` / `assertIsNotSelected()`.
- The request-on-select behavior for "Learned": `onRequestLocationPermission` must fire on the
  click **only when** `locationPermissionGranted == false` (`:176-179`), asserted by tests 3–5
  (`HomeAppSelectionSelectorTest.kt:52-96`).
- Exact prerequisite strings produced by `LearnedRankingPrerequisites.explain`:
  `"Wi-Fi logging is off"`, `"position logging is off"`, `"location permission is not granted"`,
  `"Learned ranking is inactive"`, `"classic ranking"`
  (`LearnedRankingPrerequisites.kt:29-43`; asserted at `HomeAppSelectionSelectorTest.kt:113-115`,
  `:131-133`). Do **not** reword these to add helper text.

Tags my plan touches (and explicitly must keep stable): the four `HomeAppSelection*` constants
(`SettingsScreen.kt:44-47`). The IA/grouping work must not move them into nested scopes that change
their semantics tree; keep the selector as one `Column` carrying
`HomeAppSelectionSelectorTestTag` (`:166-167`). No new tag replaces an existing value; new tags
should be additive only.

Other surfaces referenced by resource-id elsewhere in the app (keyboard, application rows) are not
part of the Settings screen, but because `testTagsAsResourceId` is global, any rename in shared
components would still have external impact. This plan does not touch them.

**UNVERIFIED:** no committed Maestro/YAML flow files exist (only `.github/workflows/pr.yml` and
`.github/dependabot.yml` match `*.yaml`) and no UiAutomator test references the settings rows beyond
`HomeAppSelectionSelectorTest`. So the only verified external resource-id consumers are the four
tags above. If out-of-repo Maestro flows target settings labels by text, changing labels would be a
risk — not verifiable from this repo.

---

## F. Minimal ordered implementation sequence

Sizes: S ≤ ~½ day, M ~1–2 days.

| Step | Work | Files | Size | Writer mode |
|---|---|---|---|---|
| 1 | **REF-08** — `remember` the sorted zone list and derive selected index from `uiState.alternativeTimeZone`; make the displayed selection reactive. No behavior change. | `SettingsScreen.kt:83-95` | S | **single writer, safe** |
| 2 | **BUG-06** — move `setWifiLoggingEnabled(true)` after a successful `registerNetworkCallback`, or roll it back in the `SecurityException` branch. Add a regression test asserting state stays false on failure. | `LogSSID.kt:22-41` | S | **single writer, safe** |
| 3 | **BUG-07 core** — remove the `SideEffect`; make the toggle grant-gated (uncheck always persists off; check persists on only on grant); add a non-toggle setter to the repository. | `SettingsScreen.kt:273-296`, `SettingsRepository.kt:26-60`, `SettingsViewModel.kt:57-75` | M | **needs design decision first** (permanently-denied UX and whether rows are disabled-but-explained) |
| 4 | **A11y / touch targets** — `toggleable` rows, 48dp minimum, `stateDescription`, merged semantics, helper text as additive nodes. | `SettingsScreen.kt:78-81`, `:284-296` | M | **single writer, safe** if helper text is additive and tags/labels unchanged |
| 5 | **Permanently-denied flow** — detect stalled grant, show message + "Open app settings" (`ACTION_APPLICATION_DETAILS_SETTINGS`). | new helper + `SettingsScreen.kt` | M | **needs design decision first** (message copy, disabled-vs-repressed policy) |
| 6 | **UX-02** — surface replace/export failure: change `replaceDatabase` to return a result, only `restart()` on success, show an error/retry surface on failure. Crosses `MainActivity` + `NeurhomeApplication`. | `NeurhomeApplication.kt:172-190`, `MainActivity.kt:78-88` | M | **needs design decision first** (error semantics, whether to re-open the DB on failure) and sequencing with BUG-04/BUG-05 (same risky block, `docs/backlog.md:237`) |
| 7 | **IA regroup** — section headers and order per D; keep the four selector tags and their tree shape. | `SettingsScreen.kt:57-127` | M | **needs design decision first** (final grouping) |
| 8 | **Tests** — extend `HomeAppSelectionSelectorTest` or add a new Compose test for grant-gated persistence and no-re-prompt; JVM tests for the repository setter. | `app/src/androidTest`, `app/src/test` | M | follows 3–5 |

Dependency note: steps 3–5 should land together (they share the permission-row component); step 1
and step 2 are independent and can ship first. Step 6 is separable but overlaps the BUG-04/BUG-05
correctness wave noted at `docs/backlog.md:237`.

---

## First file to open

`app/src/main/java/ovh/litapp/neurhome3/ui/settings/SettingsScreen.kt` — it contains the permission
component (`:273-296`), the selector (`:154-208`), the IA order (`:57-127`), and the REF-08 bug
(`:83-95`) in one place.
