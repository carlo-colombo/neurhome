# Neurhome Improvement Backlog

Generated from the 5-scout repo sweep (workflow `a0849f98-a7d0-4a69-9db1-257d3a29e685`) plus two
operator-requested features. Raw reports:

- Refactoring: `subagent-artifacts/outputs/.../task-1.md`
- Performance: `.../task-2.md`
- Errors/robustness: `.../task-3.md`
- Test coverage: `.../task-4.md`
- CI/pipeline: `.../task-5.md`

## Status

**Wave 1 (merged to `main`, build + unit tests + lint green, smoke-tested on device):**

| IDs | Outcome | Commit |
|---|---|---|
| BUG-01, BUG-02, BUG-03, BUG-16, PERF-13 | Fixed; added `ApplicationItemTagsTest` regression lock for BUG-01 | `a86e598` |
| CI-01, CI-02, CI-03, CI-04, CI-05, CI-06, CI-13, CI-14, CI-16 | Implemented; lint gate active with a 19-issue baseline; all actions SHA-pinned; `devHostSuffix` property added | `60933a2` |
| PERF-04, PERF-06, PERF-12, PERF-14 (partial) | Implemented; formatters hoisted, icon-cache lock narrowed, alarm flow off Main | `7662e7d` |
| FEAT-01, UX-01 | Design completed — see `docs/plans/feat-01-database-backup.md` and `docs/plans/ux-01-settings-permissions.md`. Not yet implemented. | — |

Remaining follow-ups opened by Wave 1:

- CI-02 uses `tools/*` / `docs/*` case patterns; the shell `case` globs do match nested paths, but consider `tools/**` style globs for clarity.
- CI-14 pinned every action including `actions/*`; the `r0adkll/sign-android-release` pin targets `master` HEAD (no release tag exists).
- Branch protection must now require the new always-on check `PR / required-checks` for CI-02's job-level filter to be safe.
- `gradle.properties` now sets `-Xmx4g` per daemon — fine for one build, reconsider before running many concurrent builds locally.
- The new `ApplicationItemTagsTest` is compiled but not yet executed on a device (`connectedDevDebugAndroidTest`).

## How to read this

| Field | Meaning |
|---|---|
| ID | `BUG`/`PERF`/`CI`/`TEST`/`REF`/`FEAT`/`UX`/`DOC` + number |
| Pri | P0 = correctness/data-safety, P1 = user-visible perf, P2 = build/CI, P3 = tests, P4 = refactor, P5 = feature/UX |
| Size | S ≈ <1 file / trivial, M ≈ a few files or schema/config, L ≈ cross-cutting |
| Ver | `verified` = parent reproduced the claim at the cited lines; `scout` = single automated report, not yet confirmed |
| Dep | must land before this item |

**Do-not-touch constraints (from AGENTS.md)** apply to every item below: `CategoryPager` pager and
back-handler semantics, tag/Uncategorized page ordering and test tags, `rebindDataLayer` ordering,
`stableListKey()` uniqueness (package + profile + launcher component), `MainActivity` root
`testTagsAsResourceId` tag constants, Room schema JSON must be committed and historical versions
never deleted, and `SettingsRepository` must keep its injectable scope.

**Build rules:** every code item ends with `./gradlew :app:assembleDevDebug`, then deploy
`app/build/outputs/apk/dev/debug/app-dev-debug.apk` and launch the package resolved from the build
output / `adb shell pm list packages` (only when a device is attached). Unit gate:
`:app:testDevDebugUnitTest`. Lint gate: `:app:lintDevDebug`.

---

## P0 — Correctness, data safety, robustness

| ID | Task | Size | Ver | Dep |
|---|---|---|---|---|
| BUG-01 | **Tag re-add silently dropped.** `initialTags` is a captured `val` that is never refreshed after a write, so restoring the original tag set skips the final `setTags`. (`ui/components/ApplicationItem.kt:139-152`) | S | verified | — |
| BUG-02 | **Calendar provider query runs on the main thread.** Cold flow with no `flowOn`, collected in `viewModelScope` (Main); every other repo read is `flowOn(IO)`. ANR/jank risk. (`data/repositories/CalendarRepository.kt:17-29`) | S | verified | — |
| BUG-03 | **Starred-contact NPE.** `displayName`/`phoneNumber` fed to non-null `Application.label` and `Uri.fromParts` with no guard; `NORMALIZED_NUMBER` is nullable by contract. (`data/dao/ContactsDAO.kt:53-70`) | S | verified | — |
| BUG-04 | **`replaceDatabase` is unsafe.** Closes the DB, then does an unguarded / unvalidated / non-atomic copy from the main thread with no try-catch, and only replaces the main file (not WAL/SHM). Bad import → relaunch crash loop or silent data loss. Copy to temp, validate `user_version` + identity hash, replace atomically, surface failure. (`application/NeurhomeApplication.kt:172-178`, `MainActivity.kt:80-88`) | M | verified | — |
| BUG-05 | **DB queried during the `replaceDatabase` close→copy→rebind window.** Eager `stateIn` flows keep collecting on IO; cancel the old repo scope before closing and/or move the swap off Main behind a lock. (`NeurhomeApplication.kt:172-178`, `NeurhomeRepository.kt:114-115,141-143,251,267`) | M | verified | BUG-04 |
| BUG-06 | **SSID logging flag left enabled on failed registration.** `setWifiLoggingEnabled(true)` runs before the `try`; a `SecurityException` returns with the flag on and no callback, so Settings reports Wi-Fi logging as enabled. (`application/LogSSID.kt:22-41`) | S | scout | — |
| BUG-07 | **Permission-backed settings store "on" while permission is denied, and re-prompt on every recomposition** via `SideEffect`. Only persist after a successful grant; request once. (`ui/settings/SettingsScreen.kt:276-297`) | S | scout | — |
| BUG-08 | **`exportDatabase` starts an Activity from an IO coroutine without `FLAG_ACTIVITY_NEW_TASK`.** Works only because the context is the Activity; an app context or a racing config change throws. (`NeurhomeRepository.kt:485-500`) | S | scout | — |
| BUG-09 | **Conditional `remember` for the tags flow** in the Uncategorized page; unsafe if the nullable VM could change. Hoist `viewModel?.tags ?: flowOf(emptyList())` and collect once. (`ui/categories/CategoryPager.kt:224-226`) | S | scout | — |
| BUG-10 | **Silent exception swallowing** in training-data parsing, contact-photo load and position geocoding — a wholly corrupt log/photo silently disables features with no signal. Add rate-limited logging per failure class. (`PairwiseHomeAppTrainingDataFactory.kt:41`, `ContactsDAO.kt:89-95`, `application/LogPosition.kt:96-104`) | S | scout | — |
| BUG-11 | **`allowBackup="true"` with empty rules** exports location/Wi-Fi/launch history to cloud backup and device transfer. Reconcile with FEAT-01 before fixing. (`AndroidManifest.xml:27-29`, `res/xml/data_extraction_rules.xml`, `res/xml/backup_rules.xml`) | S | scout | FEAT-01 decision |
| BUG-12 | **Fire-and-forget tag mutations.** `createTag`/`deleteTag`/`moveTag` launch in `viewModelScope` and swallow failures; the transactional reorder should use `NonCancellable` and surface errors. (`ui/categories/CategoryViewModel.kt:31-42`) | S | scout | — |
| BUG-13 | **Statistics slider re-runs the heavy self-join per tick** with no debounce; rapid drags queue many queries. (`AppStatisticsViewModel.kt:37-49`, `AppStatisticsScreen.kt:80-90`) | S | scout | — |
| BUG-14 | **`enableEdgeToEdge()` called before `super.onCreate`**; documented order is after. (`MainActivity.kt:36-37`) | S | scout | — |
| BUG-15 | **Calendar projection hygiene:** `OWNER_ACCOUNT` listed twice, `isPrimary` read but unused, positional index magic that breaks if columns reorder. (`data/dao/CalendarDAO.kt:19-31`, `ContactsDAO.kt:56`) | S | scout | — |
| BUG-16 | **Full UI state / contact PII logged in release builds.** `Log.d(TAG, "$uiState")` runs every recomposition; `ContactsDAO` logs name + number + primary. Remove or gate behind `BuildConfig.DEBUG`. (`ui/applications/AllApplicationsScreen.kt:29`, `data/dao/ContactsDAO.kt:58`, `ui/home/HomeViewModel.kt:76`) | S | verified | — |

> BUG-01 must be locked by TEST-07, BUG-03 by TEST-08, BUG-04/05 by TEST-03/TEST-01.

---

## P1 — Performance

| ID | Task | Size | Ver | Dep |
|---|---|---|---|---|
| PERF-01 | **Home top-apps rebuilds every 30 s and re-resolves ~64 candidates through binder icon/activity calls**, bypassing `LauncherIconCache`. Add `distinctUntilChanged`/cached resolve and drive from log changes instead of a blind timer. (`NeurhomeRepository.kt:324-344`, `HomeAppRanking.kt:64-81`, `ApplicationService.kt:62-77`, `HomeViewModel.kt:111-116`) | M | verified | — |
| PERF-02 | **`Application` is an unstable Compose type** (`Intent`/`LauncherActivityInfo`/`Any?`) so every app row is non-skippable despite strong skipping. Add a Compose stability configuration entry, or split an immutable render model. (`data/Application.kt:9-21`, `ApplicationItem.kt:119-150`) | S/M | verified | — |
| PERF-03 | **1-minute `ACTION_TIME_TICK` invalidates the whole Home tree** (clock, battery, lists, keyboard) because all sub-states fold into one `homeUIState`. Keep the clock local (`produceState`/`snapshotFlow`) and split collection. (`ClockAlarmRepository.kt:20-34`, `HomeViewModel.kt:200-232`, `WatchArea.kt:44-64`) | M | scout | REF-03 |
| PERF-04 | **`DateTimeFormatter.ofPattern` allocated inside composables** (8+ sites). Promote to file-level `val`s and `remember` formatted strings. (`WatchArea.kt:42,56,62`, `Watch.kt:40`, `Sun.kt:100`, `Weather.kt:108`, `CalendarItem.kt:76,86`, `AppStatisticsScreen.kt:106`) | S | scout | — |
| PERF-05 | **`stableListKey()` rebuilds a `String` + `flattenToString()` per call, 3–5× per row.** Cache on the model / pass one key down. (`Application.kt:24-31`, `ApplicationItem.kt:139-140,255`, `ApplicationList.kt:44`, `HomeApplicationsList.kt:32`) | S | scout | — |
| PERF-06 | **`LauncherIconCache.getOrLoad` holds `@Synchronized` across the loader callback**, serialising icon loads and blocking invalidation. Load outside the lock or per-key lock. (`LauncherIconCache.kt:19-27`) | S | scout | — |
| PERF-07 | **`Regex` recompiled on every keystroke** over the full app list. Precompile/memoise. (`HomeViewModel.kt:370-387`) | S | scout | REF-02 |
| PERF-08 | **`SELECT … GROUP BY wifi` has no index and no time bound.** Add `Index(["wifi"])` (+ migration + schema JSON) or bound by period. (`ApplicationLogEntryDao.kt`, `ApplicationLogEntry.kt:14-31`) | S/M | scout | TEST-01 |
| PERF-09 | **`deleteForTag` / tag lookups on unindexed `tagName`** (PK is `(packageName, profile, tagName)`). Add a `tagName` index (+ migration + schema JSON). (`ApplicationTagDao.kt:22-23`) | S/M | scout | TEST-01 |
| PERF-10 | **Ranking CTE runs every 30 s uncached** over a 4-month window; `mostUsedApps()` every 5 min. Cache per `(minuteOfDay, dayOfWeek, wifi)` and invalidate on log change. (`ApplicationLogEntryDao.kt`, `HomeAppRanking.kt:104-107`) | M | scout | PERF-01 |
| PERF-11 | **`applicationAndContacts` re-sorts the whole merged list on two 5-minute tickers**; add `distinctUntilChanged` and observe contacts changes instead of a blind ticker. (`NeurhomeRepository.kt:148-163,273-301`) | S | scout | — |
| PERF-12 | **`ClockAlarmRepository.alarm` binder call on Main** every 5 min. Add `flowOn(Dispatchers.IO)` (the `time` callback flow can stay on Main). (`ClockAlarmRepository.kt:19-24`) | S | scout | — |
| PERF-13 | **Lifecycle-unaware collection** on Home / AllApplications / Settings / Stats (`collectAsState()`), vs `collectAsStateWithLifecycle` in categories. (`HomeScreen.kt:39`, `AllApplicationsScreen.kt:28`, `SettingsScreen.kt:55`, `AppStatisticsScreen.kt:52`) | S | scout | — |
| PERF-14 | **Per-recomposition allocations:** `availableTags = tags.map { it.name }`, `val icons = mapOf(1..4)`, `listOfNotNull(...).joinToString(...)`. Hoist/`remember`. (`CategoryPager.kt:155,234`, `ApplicationItem.kt:307-312`, `WatchArea.kt:60-66`) | S | scout | — |
| PERF-15 | **No baseline profile / `profileinstaller`** → weaker cold start and first frame. Add a baseline-profile module + macrobenchmark. (`app/build.gradle.kts:41-54`, `libs.versions.toml`) | M | scout | CI-05 |
| PERF-16 | **Full-table Flows** `ApplicationTagDao.list()` / `AdditionalPackageMetadataDao.list()` re-emitted into hot flows. Narrow queries/observe deltas. (`ApplicationTagDao.kt:19-20`, `AdditionalPackageMetadataDao.kt:24-25`) | S | scout | — |

**Measurement plan** (run before locking in the M/L items): Compose compiler stability/metrics
report; macrobenchmark `StartupTimingMetric` + `FrameTimingMetric`; Perfetto 90 s idle trace
(expect ~0 binder calls steady-state); Layout Inspector recomposition counts; allocation profiler
for `Application`/`String`/`DateTimeFormatter`/`Regex`; Room `EXPLAIN QUERY PLAN` for
`topAppsByScore`, `mostUsedApps`, `getUniqueSSIDs`.

---

## P2 — Build, CI, pipeline

| ID | Task | Size | Ver | Dep |
|---|---|---|---|---|
| CI-01 | **Add `concurrency` + `cancel-in-progress` to `pr.yml`** (currently absent) and `cancel-in-progress` to `build.yml`. Removes superseded-run stampedes. (`pr.yml:1-18`, `build.yml:14-15`) | S | verified | — |
| CI-02 | **Add `paths-ignore` for `docs/**`, `**/*.md`, `tools/**`, `LICENSE`, `.github/dependabot.yml`** plus an always-on required job so branch protection still passes. | S | verified | — |
| CI-03 | **Collapse the three `./gradlew` invocations into one** (`assembleDevDebug testDevDebugUnitTest lintDevDebug --parallel`). (`pr.yml:37-45`) | S | verified | — |
| CI-04 | **Gate lint:** add `baseline = file("lint-baseline.xml")`, `warningsAsErrors = true`, `abortOnError = true`, upload the HTML report. 19 warnings are currently tolerated (7 `UnusedResources`). | S | scout | — |
| CI-05 | **Gradle speed config:** enable `org.gradle.caching`, uncomment `org.gradle.parallel`, raise `-Xmx` from 2048m, add explicit `kotlin.incremental`/`ksp.incremental`. (`gradle.properties:9,15`) | S | verified | — |
| CI-06 | **Stabilise the dev host suffix** so remote build cache works cross-machine: drive `-dev-<host>` from a Gradle property with a constant CI default; removes the config-time `InetAddress`/DNS lookup. (`app/build.gradle.kts:16-17,64-65`) | S | scout | — |
| CI-07 | **Validate the prod variant before release:** run `:app:testProdReleaseUnitTest` / `:app:lintRelease` on `main` so R8 + resource shrink are exercised pre-ship. (`build.yml:46-72`) | M | scout | — |
| CI-08 | **Schema-export drift gate:** fail CI when `app/schemas/**` differs after a build (`git diff --exit-code -- app/schemas`). | M | scout | — |
| CI-09 | **Add a Python job** running `python -m pytest tools/tests` with `actions/setup-python` pip caching (13 tests currently run nowhere). | S | scout | — |
| CI-10 | **Add coverage tooling (Kover or JaCoCo)**, publish XML/HTML as an artifact, start non-blocking then set a threshold from the measured baseline. | M | scout | CI-05, TEST-01 |
| CI-11 | **Run instrumented tests in CI** (emulator matrix, `svc power stayon true` per AGENTS.md) behind nightly/`workflow_dispatch` first, or replace with Robolectric where possible. | L | scout | TEST-01 |
| CI-12 | **Extract a shared reusable workflow / composite action** so `build.yml` and `pr.yml` cannot drift; delete the dead `main_project_module`/`playstore_name` env. (`build.yml:3-8,23-44`, `pr.yml:3-8,24-35`) | M | scout | — |
| CI-13 | **Artifact hygiene:** `retention-days`, SHA-256 checksum file, optional `attest-build-provenance` for releases. (`pr.yml:47-51`, `build.yml:66-72`) | S | scout | — |
| CI-14 | **Supply chain:** SHA-pin all third-party actions (only `sign-android-release` is pinned today) and add least-privilege top-level `permissions:` to both workflows. (`build.yml:23-69`, `pr.yml:24-61`) | S | scout | — |
| CI-15 | **Signing hygiene:** drop the hard-coded `BUILD_TOOLS_VERSION: "34.0.0"` (project targets SDK 37) or align it; scope keystore secrets to the release job only. (`build.yml:52-66`) | S | scout | — |
| CI-16 | **Drop the redundant `annotationProcessor(room-compiler)`** alongside `ksp(...)` in a Kotlin-only module. (`app/build.gradle.kts:110-130`) | S | scout | — |
| CI-17 | **Re-evaluate `buildConfig = true`** — disable if no `BuildConfig` field is referenced, removing a generate task. (`app/build.gradle.kts:97`) | S | scout | — |
| CI-18 | **Gate the dependabot automerge job** on `github.actor == 'dependabot[bot]'` and narrow the broad `DEPENDABOT_TOKEN` PAT. (`pr.yml:52-63`) | S | scout | — |

**Rollout order:** CI-01 → 02 → 03 → 13 → 14 → 15 (free) · CI-04 → 05 → 06 → 07 → 08 → 16 (gates) ·
CI-10 (coverage) · CI-09 → 12 → 11 (breadth).

---

## P3 — Testing and coverage

**Headline:** 149 `@Test` (≈115 JVM, 34 instrumented) but CI runs JVM-only, so every Room DAO,
migration, Compose and a11y test is unguarded. There is no Robolectric, `room-testing`,
`kotlinx-coroutines-test`, Turbine or MockK.

| ID | Task | Size | Ver | Dep |
|---|---|---|---|---|
| TEST-01 | **Test-infra foundation:** add `robolectric`, `androidx.room:room-testing`, `kotlinx-coroutines-test`, Turbine as `testImplementation`; enable `testOptions { unitTests.isIncludeAndroidResources = true }` and `testOptions.unitTests.isReturnDefaultValues` as needed. Unlocks TEST-02/03/04/05/08/09. (`gradle/libs.versions.toml`, `app/build.gradle.kts`) | M | scout | — |
| TEST-02 | **Real Room ordering tests for `TagDao`/`ApplicationTagDao`/`SettingDao`.** Today `TagRepositoryTest`'s fake re-sorts in Kotlin, so the production `ORDER BY position, name COLLATE NOCASE` and `ORDER BY tagName COLLATE NOCASE` are never executed. | M | scout | TEST-01 |
| TEST-03 | **Migration sweep v1→v19.** Only v19→v20 is tested, with a hard-coded identity hash. Validate every committed schema opens cleanly, especially the 10→11 rename and 11→12 column rename. | M/L | scout | TEST-01 |
| TEST-04 | **`ApplicationService` profile resolution / quiet modes / `toApplication`** with shadowed `LauncherApps`/`PackageManager`/`UserManager`: missing launch intent, `NameNotFoundException`, unknown profile → 0. | M/L | scout | TEST-01 |
| TEST-05 | **ViewModel state machines** (Home/Category/Settings/AllApps/Stats) with `coroutines-test` + fakes: weather/sun in-flight de-dup, calendar `flatMapLatest`, tag CRUD wiring, settings combine. | L | scout | TEST-01 |
| TEST-06 | **Category pager reorder/delete/create** on both entry paths (Home pager and `includeHome = false`): left/right buttons, confirmation dialog + cancel, blank/duplicate name errors. | M | scout | TEST-01 |
| TEST-07 | **`ApplicationItem` tag persist-on-collapse** — regression lock for BUG-01: fires once on collapse, never while open, resets when `stableListKey()` changes. | S/M | scout | BUG-01 |
| TEST-08 | **`ContactsDAO`** projection/dedup/photo cache: null name/number handling, `PHOTO_THUMBNAIL_URI` null → null icon, `groupBy(label)`, LRU eviction at 64. Regression lock for BUG-03. | M | scout | TEST-01, BUG-03 |
| TEST-09 | **`CalendarDAO` multi-day expansion** via a seeded `MatrixCursor`: 24 h edge, all-day exclusive end, null title skip, duplicate-span dedup, end-null ordering. | M | scout | TEST-01 |
| TEST-10 | **`HomeAppRanking` adapters** — `RoomHomeAppRankingSource` mapping and the learned-branch resolver inputs. | S/M | scout | — |
| TEST-11 | **Weather request-URL builder** — assert every query param; a typo renders "N/A" forever while JSON tests still pass. | S | scout | — |
| TEST-12 | **Fix brittle tests:** `CategoryPagerTest` blind `repeat(10)` swipes → deterministic settling; parameterise the migration identity hash; `WeatherForecastRowTest` permission/`°`-counting; `NeurhomeRepositorySortingTest` global `Locale` mutation. | S/M | scout | — |
| TEST-13 | **Delete the template `ExampleUnitTest` / `ExampleInstrumentedTest`.** | S | scout | — |
| TEST-14 | **Quick wins:** `FavouritesRepository` index mapping, `Application.stableListKey()`, `SettingsRepository.alternativeTimeZone` get/set. | S | scout | — |
| TEST-15 | **Keep the fixture-gated parity test from bit-rotting** — nightly job or recorded-metrics assertion (`PairwiseHomeAppChronologicalParityTest` currently `assumeTrue`-skips silently). | S | scout | CI-11 |
| TEST-16 | **Move `androidTest/data/dao` + `AppDatabaseMigrationTest` into `src/test` under Robolectric** so DB coverage lands under the existing CI gate. (The "ranking SQL needs framework SQLite" comment is obsolete — Robolectric ships real SQLite.) | M | scout | TEST-01, CI-11 |

---

## P4 — Refactoring and architecture

| ID | Task | Size | Ver | Dep |
|---|---|---|---|---|
| REF-01 | **Split the `NeurhomeRepository` god class (506 lines, ~10 responsibilities)** along existing seams: `LauncherAppsSource` (callbackFlow + icon cache), `HomeRankingCoordinator` (train/debounce/generation), `LaunchLogRepository`, `ApplicationMetadataRepository`; keep the read-model combinator. Preserve the debounce/generation semantics exactly. | L | scout | PERF-01, PERF-10 |
| REF-02 | **Move Home search/launch-log domain logic out of `ui/home`** into a domain package (`HomeQueryFilter`, `LaunchLogQuery`); they are already `internal` top-level functions, so tests move with them. (`HomeViewModel.kt:362-388`) | S | scout | — |
| REF-03 | **Extract weather/sun state from `HomeViewModel`** (438 lines, nested `combine` tower). Land the isolated weather/sun extraction first; leave ranking in place. Keep `HomeUIState` shape so the UI is untouched. | M | scout | — |
| REF-04 | **Share the duplicated application-row chrome** — 3 copies of the icon/Person-placeholder block plus duplicated `rememberPermissionState(CALL_PHONE)`. Extract `ApplicationIcon(app, size)` / `ApplicationLabelRow`. (`ApplicationItem.kt:222-250`, `AppStatisticsScreen.kt:150-175`, `ApplicationList.kt:36`, `HomeApplicationsList.kt:27`) | S/M | scout | — |
| REF-05 | **Single source for the home app count** — the literal `6` is duplicated with three different mechanisms. (`HomeApplicationsList.kt:16`, `HomeViewModel.kt:113,387`) | S | scout | — |
| REF-06 | **Standardise lifecycle-aware collection.** (Duplicate of PERF-13 — do it once.) | S | scout | — |
| REF-07 | **Replace preview-only anonymous `IHomeViewModel` implementations full of `TODO()`** with narrow state + lambda parameters or a shared stub. (`HomeScreen.kt:133-160`, `WatchArea.kt:108-152`, `ApplicationItem.kt:69-78`) | S | scout | — |
| REF-08 | **`remember` the settings time-zone sort** — `zones`/`selectedZone`/`selectedIndex` are plain `var`s, so the 600+ entry list re-sorts every recomposition. (`SettingsScreen.kt:88-97`) | S | verified | UX-01 |
| REF-09 | **Logging hygiene.** (Duplicate of BUG-16 — do it once.) | S | verified | — |
| REF-10 | **Harden `Application`:** narrow `icon: Any?` → `Drawable?` and drop the redundant `isVisible` in favour of a derived `canTrack`. Needs a product decision on Intent-only contact rows. (`data/Application.kt:15-21`, `ApplicationItem.kt:205,223`, `ContactsDAO.kt:68`) | M | scout | REF-04 |
| REF-11 | **Delete the dead `getTopApps(n)` single-arg overload** (no callers) and any unused model field. (`NeurhomeRepository.kt:316-319`) | S | scout | PERF-01 |
| REF-12 | **Replace `moveTag(name, direction: Int)` magic `-1/+1`** with a `TagMoveDirection` enum (add as an overload first). (`CategoryViewModel.kt:32`, `CategoryPager.kt:82-83`, `TagRepository.kt:49`) | S | scout | — |
| REF-13 | **Shrink `NeurhomeApplication` (224 lines)** — move the debug `logcat` process spawn into the `debug` source set and extract a `DataLayerGraph` owning the `@Volatile` lazies + rebind. Keep `rebindDataLayer` ordering intact. (`NeurhomeApplication.kt:167-226`) | M | scout | BUG-04 |
| REF-14 | **Naming cleanups:** `AllApplicationsViewModel.UiState` → `AllApplicationsUiState`; `ApplicationList.kt` → `ApplicationsList.kt`; settings `Settings` snapshot → `SettingsUiState`; normalise `filteredUiState`/`filteredUIState` casing. | S | scout | — |
| REF-15 | **Align `AppStatisticsViewModel` with ViewModel conventions** and de-duplicate the two independent `Calendar.getInstance()` "today" defaults. (`AppStatisticsViewModel.kt:19,22,63`) | S | scout | — |

**Sequencing rule:** never batch REF-03 (HomeViewModel) with REF-01 (repository) — both change Home
constructors and would make regressions un-bisectable.

---

## P5 — Features and UX

| ID | Task | Size | Ver | Dep |
|---|---|---|---|---|
| FEAT-01 | **Automatic Android-system backup of the database.** Make backup deliberate rather than accidental: decide what the Auto Backup set contains, wire `dataExtractionRules` (API 31+) and `fullBackupContent` (API ≤30) include/exclude domains, and add a user-visible control. See detail below. | M | operator | BUG-11 |
| UX-01 | **Settings/permission usability review.** Reduce permission friction, stop storing settings "on" without a grant, remove the re-prompt loop, improve grouping/labelling and a11y while keeping every existing `testTag` stable. See detail below. | M | operator | — |
| UX-02 | **Surface DB-replace failure** with a recoverable message instead of an unconditional `restart()`. (`MainActivity.kt:80-88`) | S | scout | BUG-04 |
| UX-03 | **Bound the debug logcat capture file** (currently never stops, grows unbounded). (`NeurhomeApplication.kt:214-222`) | S | scout | REF-13 |
| DOC-01 | **Document the dev host-suffix property and build-cache use** once CI-06 lands. | S | — | CI-06 |
| DOC-02 | **Record the backup policy decision** (what is backed up and why) next to the data-safety rules in AGENTS.md. | S | — | FEAT-01 |

### FEAT-01 detail — automatic database backup

Full design: `docs/plans/feat-01-database-backup.md` (recommends an immediate exclude-database
stopgap, then splitting the sensitive launch history into a second Room database).

**Current state (verified):** `android:allowBackup="true"`, and `dataExtractionRules` /
`fullBackupContent` point at the untouched template files, which are empty. An empty rules file
means *include everything* — so today the entire Room database (launch history, geocodes/positions,
Wi-Fi SSIDs) is already uploaded to cloud backup and copied on device transfer. That is BUG-11.

**Decision required (product/privacy):** a Room DB is one file, so you cannot back up "the useful
tables" and exclude the sensitive ones in place. Pick one:

- **A — Full DB backup.** Simple; keeps tags, aliases, visibility, favourites and settings across
  reinstall/device transfer. Also exports location/Wi-Fi history. Add `requireFlags="clientSideEncryption"`
  and a user-facing toggle + explanation.
- **B — Backup with the sensitive log excluded.** Requires splitting sensitive history into a second
  Room database (or a separate file) so the include/exclude domains can differ. More work, best privacy.
- **C — No cloud backup; local export only.** Rely on the existing Settings → export database flow
  (harden it via BUG-04). Zero cloud exposure; users can lose state on reinstall.

**Acceptance criteria (any option):** explicit non-empty `backup_rules.xml` and
`data_extraction_rules.xml`; a Robolectric/unit test asserting the exclude domains; a manual
`bmgr backupnow` / restore smoke check; AGENTS.md note added (DOC-02); BUG-11 resolved in one
direction or the other.

### UX-01 detail — settings and permissions

Full audit and plan: `docs/plans/ux-01-settings-permissions.md` (10-row inventory, verified defect
list, request-once and grant-gated persistence model, disabled-but-explained recommendation, and
the testTag regression surface).

Known concrete defects to fold into this review: BUG-07 (preference persisted while the permission
is denied; `SideEffect` re-prompt loop), REF-08 (unremembered zone sort), BUG-06 (Wi-Fi logging
reports enabled after a failed registration), BUG-16 (restore/export surfaced without failure
handling → UX-02).

Scope for the review: inventory of every permission-backed row and its request path; whether each
toggle should be disabled-but-explained rather than checked-but-ignored; permission rationale and
"permanently denied → open app settings" flows; grouping and ordering of the settings list;
accessibility labels and touch targets; and a check that no existing `testTag` or `testTagsAsResourceId`
name changes (Compose tests and UiAutomator depend on them).

---

## Cross-report agreements (high confidence)

Independently reported by two or more scouts:

1. Lifecycle-unaware `collectAsState()` on four screens — PERF-13 / REF-06.
2. Debug logging of full UI state and contact PII — BUG-16 / REF-09.
3. Unremembered time-zone sort in Settings — REF-08.
4. Instrumented tests never run in CI — TEST headline / CI-11.
5. Lint is not a gate — CI-04.
6. No coverage tooling or threshold — CI-10 / TEST-01.

## Suggested execution waves

| Wave | Contents | Rationale |
|---|---|---|
| 1 | BUG-01, BUG-02, BUG-03, BUG-16 | Verified, small, disjoint files, one build |
| 2 | CI-01…CI-06, CI-13, CI-14, CI-16 | Zero product risk, no device needed |
| 3 | TEST-01 + TEST-16 | Unlocks the largest coverage jump; feeds every later refactor |
| 4 | PERF-04, PERF-05, PERF-06, PERF-07, PERF-12, PERF-13 | Cheap constant-factor wins |
| 5 | BUG-04 + BUG-05 + UX-02 | The risky correctness block, with BUG-13/TEST-14 around it |
| 6 | FEAT-01 + BUG-11 + DOC-02, UX-01 + BUG-07 + REF-08 | Operator-requested features |
| 7 | PERF-01, PERF-02, PERF-03, PERF-10 | Structural performance |
| 8 | REF-01…REF-15 | Refactors, one axis at a time |
| 9 | CI-10, CI-07, CI-08, CI-09, CI-12, CI-11 | Coverage gate and CI breadth |
