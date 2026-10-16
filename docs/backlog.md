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
- ~~Branch protection must now require the new always-on check `PR / required-checks`~~ — **closed by decision: not wanted.** This project is trunk-based and PRs exist mainly for dependabot, so branch protection is deliberately not configured. `required-checks` stays in the workflow as a harmless always-on signal but is not a gate; do not re-raise it.
- `gradle.properties` now sets `-Xmx4g` per daemon — fine for one build, reconsider before running many concurrent builds locally.
- The new `ApplicationItemTagsTest` is compiled but not yet executed on a device (`connectedDevDebugAndroidTest`).

**Waves 2–4 (merged).** JVM gate: 145 tests, 0 failures, lint clean with no baseline change.
On-device: 36/36 instrumented tests pass.

| IDs | Outcome | Commit |
|---|---|---|
| FEAT-01, BUG-11 | Explicit backup rules covering every domain incl. the database and launch log; `requireFlags="clientSideEncryption"` on every cloud-backup include; Settings backup-status panel (secure lock screen, destination, payload vs 25 MB cap, what is included); custom `NeurhomeBackupAgent` gzip payload with CSE re-enforced in code and a device-transfer guard. Payload lives beside the database because `fullBackupFile` ignores cache files. BUG-11 closed as intended. | `32d8e12`, `c1dd2df` |
| FEAT-02 | Dropped the unused `packageName` index (schema v21, auto-generated migration) and added 2-year launch-log retention with a freelist-guarded `VACUUM`. Measured `26,079,232 -> 7,290,880` bytes (~72% smaller). | `45a8186` |
| PERF-08 | Added a covering `wifi` index (schema v22) for `getUniqueSSIDs()`, which previously did a full table scan plus a temp b-tree. Measured 5.3 ms -> 0.9 ms for +0.63 MB raw / +0.11 MB compressed. | `7568804` |
| CI-18 | The `automerge` job is now skipped at job level for non-dependabot PRs instead of starting a runner to log a no-op. | `8fd26a3` |

**Correction to the FEAT-02 rationale (measured after the fact).** Retention was originally justified
as the fix for the 25 MB cloud quota. That was wrong: the agent gzips the payload, and the *original*
26,079,232-byte database compresses to **6,470,721 bytes (24.7% of the cap)**, so compression alone
resolves the quota. Retention's real payoff is local disk, memory, query cost and data minimisation
(and it takes the backup to 7.8% of the cap). This matters for PERF-08: the index costs only ~0.11 MB
compressed, so the quota is not a reason to withhold it. With retention the DB gzips to 2.03 MB and
with the wifi index to 2.14 MB — roughly 12x headroom.
| UX-01, BUG-06, BUG-07, REF-08 | Request-once permission rows, grant-gated persistence, revoke-clears-stale-opt-in, accessible rows, remembered time-zone list, non-blank zone field, SSID flag only set after successful registration. No testTag changed. | `78488a1` |
| TEST-01, TEST-12, TEST-13 | Robolectric + real in-memory Room + `kotlinx-coroutines-test` + Turbine; `robolectric.properties` pins `sdk=34` so tests run offline; both template tests deleted; migration/pager/weather tests made deterministic and structural. | `6fbd7e5`, `329e818` |

**Wave 5 (merged).** JVM gate: **162 tests, 0 failures**. Instrumented: **25/25** on an API 36
emulator (down from 36 because 11 tests moved to the JVM).

| IDs | Outcome | Commit |
|---|---|---|
| BUG-04, BUG-05, UX-02 | Settings → Replace database now stages the picked file into `databases/`, validates it read-only before anything is touched (SQLite open, `user_version` within the migratable range, `PRAGMA quick_check(1)`), and only then quiesces the data layer, closes, does an atomic move, deletes stale sidecars and rebinds. Failures leave the live database untouched, reopen it, and surface a message instead of relaunching. | `a766810` |
| TEST-16 | The two DAO tests and the migration test moved to `src/test` under Robolectric with real SQLite. No assertion loosened; the migration test still drives v19 → v22 and pins the exact index set. The obsolete "instrumentation-only because the ranking SQL needs framework SQLite" claim is gone. | `574753f` |
| BUG-08, BUG-09, BUG-10, BUG-12 | `exportDatabase` chooser can no longer throw from an application context; the conditional `remember` in the Uncategorized page is gone; silent `runCatching` swallowing now logs once per failure class; tag writes log failures and the reorder transaction is `NonCancellable` so a torn-down scope cannot leave a partial position order. | `0e0d86c` |

**Wave 6 (merged and released).** JVM gate: **213 tests, 0 failures**. Instrumented: **29/29**.

| IDs | Outcome | Commit |
|---|---|---|
| PERF-03, REF-02, REF-03, REF-05 | `HomeViewModel` 438 → 274 lines. Weather/sun extracted to `WeatherSunHolder` with its de-duplication and sharing semantics intact; the pure query/launch-log helpers moved to a `domain.home` package with the search Regex memoised instead of recompiled per call; and the once-a-minute tick no longer flows through `homeUIState`, so a minute no longer invalidates the whole Home tree — `WatchArea` reads the clock locally on the minute boundary, with the boundary math unit-tested (including the never-zero-delay spin guard). | `8c4cd8a` |
| PERF-01, PERF-05, PERF-10, PERF-11, REF-11 | The blind 30 s top-apps rebuild and the 5-minute polling sort are gone: the ranking recomputes on launch-log writes, launcher emissions and the local minute, with `distinctUntilChanged`. A **resolved-row cache was required to make de-duplication possible at all** — the old resolver returned a fresh `Drawable`/`LauncherActivityInfo` per call, so lists could never compare equal. `Application.listKey` is cached behind the unchanged `stableListKey()`, so call sites are untouched. Dead single-arg `getTopApps` overload deleted. | `f6b49b3` |
| TEST-02, TEST-09, TEST-10, TEST-11, TEST-14 | 37 new JVM tests. The tag-ordering invariant now runs against **real SQL** instead of a fake DAO that re-implemented the ordering in Kotlin. Calendar multi-day expansion and the ranking adapters are covered, and the weather request URL is now extracted (`WeatherForecastUrl`) and asserted rather than never checked. | `74b3a6f` |

**New finding from wave 6 (PERF-17, measured, not inferred).** The app never runs `ANALYZE`. A throwaway Robolectric probe showed both ranking reads use `index_ApplicationLogEntry_user_packageName_timestamp` only as a **full covering index scan**, because `user` leads the index so `timestamp > date('now', :period)` is evaluated during the scan rather than used as a range seek. After `ANALYZE` the planner upgrades both reads to `SEARCH ... (ANY(user) AND ANY(packageName) AND timestamp>?)`. In flight in wave 7.

Remaining follow-ups after waves 2–5:

- **The backup agent's cloud path is UNVERIFIED and cannot be verified locally.** The local `bmgr` transport does not advertise client-side encryption, and the agent deliberately uploads nothing without CSE, so `bmgr` exercises nothing. Validating it needs a real device with a secure lock screen, Google backup enabled, and a full backup/restore cycle. Six specific unverified items are listed in `docs/plans/feat-01-database-backup.md` §G.8, with an adb procedure in §G.7.
- **Android lint's `FullBackupContent` check** rejects an `<exclude>` whose domain has no matching `<include>`, and a section with any `<include>` is narrowed to exactly those paths. This is why the database is *omitted* from `<cloud-backup>` rather than excluded, and why `onFullBackup` must re-enforce CSE in code.
- **The JVM story is now 21 end to end** (daemon, CI, app bytecode target), documented in AGENTS.md. The remaining caveat is that the CI `distribution: jetbrains` change can only be confirmed by a real CI run.
- **The full database swap itself is verified by reasoning, not by test.** The pre-swap validator has 6 JVM tests covering accept/reject paths, but quiescing the live data layer, the atomic move and the rebind are not unit-tested end to end.
- ~~**Branch protection must require the new always-on check `PR / required-checks`** for CI-02's job-level path filter to be safe.~~ — **closed by decision: not wanted.** Trunk-based development; PRs are mostly dependabot. Not a gap.
- ~~**`PRERELEASE: true` looks like a no-op**~~ — **closed by decision: full releases are intended.** `PRERELEASE: true` in build.yml exists to shape the *tag name* (`PRERELEASE_SUFFIX: build` produces `1.1.0-build.N`); it is not meant to mark the GitHub release as a prerelease. Publishing every push as a normal "Latest" release is the deliberate behaviour, so do not "fix" it to `prerelease: true`.
- A failed `test-infra` lane's worktree is preserved at `worktrees/neurhome/pi-worktree-55fead5b-...` with its partial diff at `/tmp/neurhome-test-infra-partial-55fead5b.patch`; its useful content was re-derived and merged, so it can be removed once the parent confirms nothing else is needed.

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
| BUG-11 | **CLOSED — working as intended.** `allowBackup="true"` with empty rules exports location/Wi-Fi/launch history to cloud backup and device transfer. Operator decision: this is the desired behaviour. Remaining work is only to make the rules explicit and documented — tracked by FEAT-01. (`AndroidManifest.xml:27-29`, `res/xml/data_extraction_rules.xml`, `res/xml/backup_rules.xml`) | S | verified | FEAT-01 |
| BUG-12 | **Fire-and-forget tag mutations.** `createTag`/`deleteTag`/`moveTag` launch in `viewModelScope` and swallow failures; the transactional reorder should use `NonCancellable` and surface errors. (`ui/categories/CategoryViewModel.kt:31-42`) | S | scout | — |
| BUG-13 | **Statistics slider re-runs the heavy self-join per tick** with no debounce; rapid drags queue many queries. (`AppStatisticsViewModel.kt:37-49`, `AppStatisticsScreen.kt:80-90`) | S | scout | — |
| BUG-14 | **INVALID — not a defect.** The claim was that `enableEdgeToEdge()` is called before `super.onCreate()` and the documented order is after. The pinned androidx.activity version's own sources-jar KDoc documents exactly the current order (`enableEdgeToEdge()` then `super.onCreate()`); the Android developer guide shows the other order. Two documents disagreeing is not a defect, and reordering is unverifiable without a device, so the call stays and a comment records why. | S | **disproven** | — |
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
| CI-18 | **Gate the dependabot automerge job** on `github.actor == 'dependabot[bot]'` and narrow the broad `DEPENDABOT_TOKEN` PAT. (`pr.yml:52-63`) DONE - the job is now skipped at job level for non-dependabot PRs; narrowing the PAT is still open. | S | scout | — |
| CI-19 | **A third-party action still emits a deprecated command.** `r0adkll/sign-android-release` uses `::set-output`, which GitHub has deprecated; a future removal would break release signing independently of anything in this repo. Observed on every release run. Since the action is SHA-pinned and unmaintained, consider replacing it with explicit `zipalign` + `apksigner` steps, which the workflow already resolves build-tools for. | S/M | audit | — |
| CI-20 | **Build-tools resolution now tracks the runner image's newest version** (37.0.0 today, resolved dynamically to replace the hard-coded `34.0.0`). This is strictly more robust than the literal, but it means signing behaviour follows the runner image. It currently survives a partially-installed newest build-tools only because the resolution script checks that `apksigner` is executable. Worth a comment or a documented version floor. | S | audit | CI-15 |
| CI-21 | **`lint-baseline.xml` contains one entry no longer found in the project.** Informational today - CI logs `1 errors/warnings were listed in the baseline file but not found; perhaps they have been fixed?` - but it is noise on every run and will mask a genuine baseline-drift signal. Regenerate or prune the baseline. | S | audit | — |

**Rollout order:** CI-01 → 02 → 03 → 13 → 14 → 15 (free) · CI-04 → 05 → 06 → 07 → 08 → 16 (gates) ·
CI-10 (coverage) · CI-09 → 12 → 11 (breadth).

---

## P3 — Testing and coverage

**Headline (rewritten after the independent oracle audit at `b617bba`):** 246 tests, 217 JVM and 29
instrumented. The original gap is closed: Robolectric, `androidx.room:room-testing`,
`kotlinx-coroutines-test` and Turbine are wired up, and the DAO/migration tests now run on the JVM
under the CI gate. The instrumented suite still only runs on a device.

The audit's verdict: the suite is substantially better than the failure modes it was asked to hunt
for, and its weaknesses are concentrated in **three** places - a shadow that replaces the method
under test, a mirrored fake at the repository boundary, and an 18-version hole in migration
coverage. Findings below are marked `audit`; `scout` items predate the audit.

**Do NOT weaken these - the audit identified them as genuinely adversarial:**
`WeatherForecastUrlTest` (pins the URL and cross-checks fields against the serializer descriptor) ·
`BackupRulesTest` (parses the real XML and manifest) · `LogRetentionTest` (leap-day clamp plus the
lexicographic-ordering property the SQL depends on) · `LogRetentionAnalyzeTest` (proves `ANALYZE`
via `sqlite_stat1`, not by re-planning) · `SettingDaoTest`'s `CyclicBarrier` concurrency test ·
`HomeAppRankingTest`'s anti-tautology note on the over-fetch formula · `HomeAppRankingAdaptersTest`'s
fake that **throws** for unused queries (adopt as house style) · `CalendarDaoTest`'s
exact-projection assertion · the Compose suite, especially `ApplicationItemTagsTest`'s exact write
sequence (the BUG-01 guard) and `HomeApplicationsListTest`'s scroll assertion.

**Disproven by the audit:** the `scout` claim that `NeurhomeRepositorySortingTest` leaks the global
`Locale` is **wrong** - it saves and restores in `@Before`/`@After`, and its Turkish `U+0130`
coverage is real. Do not "fix" it.

**Limits of the audit:** it executed **no** tests, so every "would this fail if X regressed" verdict
is source reasoning, not mutation testing. TEST-26 recommends mutation runs to settle what is left.

| ID | Task | Size | Ver | Dep |
|---|---|---|---|---|
| TEST-01 | **Test-infra foundation:** add `robolectric`, `androidx.room:room-testing`, `kotlinx-coroutines-test`, Turbine as `testImplementation`; enable `testOptions { unitTests.isIncludeAndroidResources = true }` and `testOptions.unitTests.isReturnDefaultValues` as needed. Unlocks TEST-02/03/04/05/08/09. (`gradle/libs.versions.toml`, `app/build.gradle.kts`) | M | scout | — |
| TEST-02 | **Real Room ordering tests for `TagDao`/`ApplicationTagDao`/`SettingDao`.** DAO-level coverage DONE, but the audit found the mirror survives at the repository boundary: `TagRepositoryTest`'s fake still re-sorts in Kotlin, and `TagRepository.moveTag` derives its index from `tags.first()`, so it **depends on the DAO's ordering** - `movingTagsPersistsOrderAndRespectsBoundaries` would still pass if the production `ORDER BY` were deleted. Also `FakeApplicationTagDao.listForApplication` omits the production `ORDER BY tagName COLLATE NOCASE` and returns insertion order. Needs: run `moveTag`/`deleteTag` against real in-memory Room. | M | audit | TEST-01 |
| TEST-03 | **Migration sweep.** CONFIRMED BY THE AUDIT AS THE LARGEST GAP IN THE REPO: `AppDatabase.kt` declares **26 `AutoMigration`s** and `app/schemas` commits **23** versions, but only **19 to 23** is tested. The 18 earlier hops would brick upgrades for existing users with nothing failing. Build a `MigrationTestHelper` sweep over every committed schema. That also justifies or condemns the dead `assets.srcDir(schemas)` block in TEST-24. | M/L | audit | TEST-01 |
| TEST-04 | **`ApplicationService` profile resolution / quiet modes / `toApplication`.** ATTEMPTED AT THE WRONG LEVEL - must be redone. `ShadowApplicationService` uses `@Implements(ApplicationService::class)` to replace `toApplication` **itself**, so the real body never executes in any test: profile lookup returns null, missing launch intent, unresolvable activity, `NameNotFoundException`, the profile-0 fallback and the label/icon assembly are all unverified. A regression silently mis-attributes apps in Home and Favourites with nothing failing. The seam is right but the level is wrong - shadow `LauncherApps`/`PackageManager`/`UserManager` so the real method runs. | M/L | audit | - |
| TEST-05 | **ViewModel state machines** (Home/Category/Settings/AllApps/Stats) with `coroutines-test` + fakes: weather/sun in-flight de-dup, calendar `flatMapLatest`, tag CRUD wiring, settings combine. | L | scout | TEST-01 |
| TEST-06 | **Category pager reorder/delete/create** on both entry paths (Home pager and `includeHome = false`): left/right buttons, confirmation dialog + cancel, blank/duplicate name errors. | M | scout | TEST-01 |
| TEST-07 | **`ApplicationItem` tag persist-on-collapse** — regression lock for BUG-01: fires once on collapse, never while open, resets when `stableListKey()` changes. | S/M | scout | BUG-01 |
| TEST-08 | **`ContactsDAO`** projection/dedup/photo cache: null name/number handling, `PHOTO_THUMBNAIL_URI` null → null icon, `groupBy(label)`, LRU eviction at 64. Regression lock for BUG-03. | M | scout | TEST-01, BUG-03 |
| TEST-09 | **`CalendarDAO` multi-day expansion** via a seeded `MatrixCursor`: 24 h edge, all-day exclusive end, null title skip, duplicate-span dedup, end-null ordering. | M | scout | TEST-01 |
| TEST-10 | **`HomeAppRanking` adapters** — `RoomHomeAppRankingSource` mapping and the learned-branch resolver inputs. | S/M | scout | — |
| TEST-11 | **Weather request-URL builder** — assert every query param; a typo renders "N/A" forever while JSON tests still pass. | S | scout | — |
| TEST-12 | **Fix brittle tests.** DONE - deterministic `waitUntil` settling, parameterised identity hash, structural weather assertions, template tests deleted. One part is **DISPROVEN**: the claim that `NeurhomeRepositorySortingTest` mutates the global `Locale` is wrong; it already saves and restores. | S/M | scout, one **disproven** | - |
| TEST-13 | **Delete the template `ExampleUnitTest` / `ExampleInstrumentedTest`.** DONE. | S | scout | - |
| TEST-14 | **Quick wins:** `FavouritesRepository` index mapping, `Application.stableListKey()`, `SettingsRepository.alternativeTimeZone` get/set. DONE. | S | scout | - |
| TEST-15 | **Keep the fixture-gated parity test from bit-rotting.** CONFIRMED BY THE AUDIT: `PairwiseHomeAppChronologicalParityTest` `assumeTrue`-skips on every run, so **192 lines of assertions execute nowhere** and the audit could not assess them. Needs a nightly job or a recorded-metrics assertion. | S | audit | CI-11 |
| TEST-16 | **Move `androidTest/data/dao` + `AppDatabaseMigrationTest` into `src/test` under Robolectric.** DONE - 11 tests moved, JVM count rose 145 to 162 while the instrumented count fell by exactly the same 11. | M | scout | - |
| TEST-17 | **`FakeSettingDao` is not a faithful double, and its production caller is untested.** `insertOverride` and `upsert` have byte-identical fake bodies although production uses `@Insert(REPLACE)` versus `@Upsert`; `like()` reduces SQL `LIKE` to `contains(trim('%'))`, which is not equivalent, since SQLite `LIKE` is ASCII-case-insensitive and treats `%` as a wildcard anywhere. Worse, `insertOverride`'s only production caller - `FavouritesRepository.setFavourite` - is invoked by no test. Real `like()` coverage happens to exist via `FavouritesRepositoryTest`, so this is a fidelity hazard rather than a hole. | S/M | audit | - |
| TEST-18 | **`DatabaseCandidateValidatorTest` misses the corrupt-but-openable branch.** `quickCheckPasses` returning false is never exercised, so that branch could be deleted and every test would still pass - meaning the scribbled-page case the KDoc claims to catch is unproven. Add a valid SQLite header with a corrupted page. | S | audit | - |
| TEST-19 | **`HomeApplicationsListTest` does not pin the collapsed layout.** The claim that `fillMaxSize()` plus `verticalScroll` leaves the collapsed `SpaceEvenly` spacing identical to the old column is unasserted, so a spacing regression would pass. | S | audit | - |
| TEST-20 | **The minute tick is not asserted - DELIBERATELY NOT COVERED, with the reason recorded.** `WatchMinuteTickTest` tests the `millisUntilNextMinute` arithmetic (including the never-zero-delay guard); nothing verifies `WatchArea` drives it, so deleting the tick would not fail a test. Two candidate tests were evaluated and both rejected: (a) a real-clock Compose test that waits for the display to change is honest and would catch the regression, but costs up to 65 seconds of device time and becomes the slowest, most timing-dependent test in the repo; (b) adding a time-source seam does **not** buy determinism, because `delay` inside `produceState` runs on the Android main dispatcher, which `composeTestRule.mainClock` does not virtualise - `MainTestClock` advances the frame clock, not coroutine `delay`. A real seam would have to inject the time source **and** the delay, which is disproportionate for a behaviour whose regression (a frozen clock) is immediately visible. Revisit only if a time seam lands for other reasons. | S | audit | - |
| TEST-21 | **`DatabaseBackupPayloadTest` covers the helpers, not the agent.** `NeurhomeBackupAgent.onFullBackup`/`onRestoreFile` - the `wal_checkpoint`, the no-close fd handling, the atomic `Files.move`, sidecar deletion and `forgetInstance()` - have zero coverage. Defensible because the transport cannot be faked, but the class name reads as end-to-end backup coverage. | M | audit | - |
| TEST-22 | **Delete or fold `AppDatabaseJvmTestStackTest`.** Its two DAO round-trips are strictly weaker duplicates of `SettingDaoTest`/`TagDaoTest`; the KDoc is honest but it inflates the count without protecting behaviour. | S | audit | - |
| TEST-23 | **`ApplicationStableListKeyTest` asserts a pure function equals itself** (`assertEquals(first, second)` on the same input). Drop that line; keep the property tests, which are the genuine guards. | S | audit | - |
| TEST-24 | **Dead test-asset block.** `app/build.gradle.kts` maps `app/schemas` into test assets with a comment referencing `MigrationTestHelper`, which is used **nowhere** in the repo. Either wire it up via TEST-03 or delete it. | S | audit | TEST-03 |
| TEST-25 | **JVM tests run at API 34 while the app compiles and targets 37.** The `robolectric.properties` `sdk=34` pin keeps them offline and fast, which is a sound trade, but it means API 35+ behaviour (notably enforced edge-to-edge) is uncovered - a divergence that should be a documented constraint rather than an invisible one. | S | audit | - |
| TEST-26 | **Adopt mutation testing as a standing technique.** The audit ran no tests, so its verdicts are source reasoning. Deleting a production branch and confirming a test fails would settle TEST-17 through TEST-23 definitively, and guard against future coverage inflation. | M | audit | - |
| TEST-27 | **The ranking debounce/generation semantics have no test.** `REF-01` extracted them into `HomeRankingCoordinator` and preserved them verbatim, but nothing pins the property that matters: *a refresh requested while a training pass is already in flight is not lost*. The lane declined to write speculation around them, which was correct - this needs a virtual-time test driving `requestHomeModelRefresh` during an in-flight train. Until it exists, that behaviour is preserved only by inspection. | M | audit | REF-01 |

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
| REF-10 | **Harden `Application` and normalise Intent-only contact rows — DONE** (released). Operator decision: contact rows backed by a phone Intent (no `appInfo`) **are tracked and ranked exactly like apps**. Landed: `icon` narrowed to `Drawable?`; stored `isVisible` replaced by derived `canTrack = appInfo != null || intent != null`; each contact row's identity is the call URI, which is exactly what `launch()` logs, so row, log and ranking cache agree by construction; an identity-first resolver matches logged keys against contact rows so a `tel:` key never reaches `LauncherApps`; and intent-only rows are candidates going through the **same** quiet-profile and `<hideFrom="TOP">` filters as apps, with no exemption. Previously logged `tel:` rows are **not** orphaned and now become rankable. (`data/Application.kt`, `data/dao/ContactsDAO.kt`, `HomeAppRankingAdapters.kt`, `ui/NeurhomeViewModel.kt`) | M | verified | REF-04 |
| REF-16 | **Favourites and statistics still resolve key-by-package only.** `FavouritesRepository` resolves its slots solely through `ApplicationService.toApplication`, which returns null for a `tel:` key, so favouriting a starred contact now stores `tel:…` and renders an **empty slot** (before the change it stored the dialer package and showed the *dialer app* — equally wrong, which is why it went unnoticed). The statistics simulation has the same shape, so contacts never appear there. Both need the same identity-first resolution the ranking path got. In flight. | M | verified | REF-10 |
| DATA-01 | **Orphaned contact metadata keyed by the dialer package — needs a product decision.** Metadata written *before* REF-10, when contacts were identified by `com.google.android.dialer` (notably a hide-from-top row), is now orphaned for the contact and can still hide the **real dialer app** from the top list. The same ambiguity applies to a contact favourite stored before REF-10: it holds the dialer package and still renders the **dialer app** in that slot. Neither case can be reliably distinguished from a legitimate dialer-app hide/favourite, so a heuristic cleanup would be guesswork. Either accept it, or give the user a way to clear stale metadata. Do not "fix" it with a guess. | S | verified | REF-10 |
| CI-22 | **`lintVitalProdRelease` reports 17 baseline entries it cannot match**, because the baseline was created for the `lint` variant while the vital pass runs a different variant (`Creation variant: lint` vs `Current variant: lintVitalRelease`). Cosmetic noise on every release run. A lane proposed silencing it by promoting seven checks to `fatal`, which changes lint severity semantics rather than fixing the mismatch and was **rejected as unverified and potentially build-breaking**; the honest fix is to regenerate or split the baseline per variant. | S | verified | — | Work: narrow `icon: Any?` → `Drawable?`; replace the stored `isVisible` with a derived `canTrack = appInfo != null || intent != null`; give each contact row a stable identity equal to the key `launch()` logs; teach ranking to resolve that key back to the row; and stop filtering intent-only rows out of candidates merely for having no `appInfo`. See the identity trap below. (`data/Application.kt:15-21`, `ApplicationItem.kt:205,223`, `ContactsDAO.kt:68`) | M | verified | REF-04 |

**The identity trap behind REF-10 (why a flag flip would not have worked).** `ContactsDAO` builds rows with `packageName = "com.google.android.dialer"` (a hardcoded dialer package), but `NeurhomeViewModel.launch()` logs a contact launch using `intent.data.toString()` as the key — i.e. `tel:+39…`. Ranking resolves logged keys back to rows via `ApplicationService.toApplication(packageName)`, which uses `getLaunchIntentForPackage` + `launcherApps.resolveActivity`. A `tel:` key resolves to nothing, and the hardcoded dialer package would have resolved to the dialer rather than the contact. So contacts were **logged but never rankable**. Normalisation requires the row identity and the logged key to be the same string, and the resolver to map it back to the row.
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
| FEAT-02 | **Keep the database under the 25 MB Auto Backup cloud quota — currently at/over it.** Measured on `neurhome_database_sample.db`: 26,079,232 B total, of which `ApplicationLogEntry` is 10,838,016 B and its three indexes 15,155,200 B, i.e. **99.7% of the file is the launch log and its indexes**. Android caps cloud backup at 25 MB per app, so the database very likely exceeds the cap and cloud backup of it is silently skipped. Needs a log retention policy plus an index review. See detail below. | M | verified | FEAT-01 |
| UX-01 | **Settings/permission usability review.** Reduce permission friction, stop storing settings "on" without a grant, remove the re-prompt loop, improve grouping/labelling and a11y while keeping every existing `testTag` stable. See detail below. | M | operator | — |
| UX-02 | **Surface DB-replace failure** with a recoverable message instead of an unconditional `restart()`. (`MainActivity.kt:80-88`) | S | scout | BUG-04 |
| UX-03 | **Bound the debug logcat capture file** (currently never stops, grows unbounded). (`NeurhomeApplication.kt:214-222`) | S | scout | REF-13 |
| DOC-01 | **Document the dev host-suffix property and build-cache use** once CI-06 lands. | S | — | CI-06 |
| DOC-02 | **Record the backup policy decision** (what is backed up and why) next to the data-safety rules in AGENTS.md. | S | — | FEAT-01 |

### FEAT-02 detail — the 25 MB cloud quota is the binding constraint

Measured directly with `sqlite3` against the committed `neurhome_database_sample.db` (not a guess):

| Component | Bytes | Share of file |
|---|---:|---:|
| **Whole database file** | **26,079,232** (4096 B x 6367 pages ≈ 24.9 MiB) | 100% |
| `ApplicationLogEntry` table | 10,838,016 | 41.6% |
| `index_..._user_packageName_timestamp` | 7,208,960 | 27.6% |
| `index_..._packageName` | 4,071,424 | 15.6% |
| `index_..._timestamp` | 3,874,816 | 14.9% |
| Everything else (settings, tags, aliases) | ~86,016 | 0.3% |

`ApplicationLogEntry` holds 106,084 rows spanning 2020-04-08 to 2026-09-30. The table plus its
three indexes is 25,993,216 B — essentially the entire file.

Consequences:

- Android Auto Backup allows **25 MB per app** for cloud backup. At 24.9 MiB the database is at
  ~99.5% of a 25 MiB cap and above a 25 MB decimal cap, so the framework is very likely skipping
  the backup already, or will within weeks of continued logging. "Back up everything" is therefore
  not currently achievable through cloud backup, and enabling client-side encryption does not change
  the quota. Device-to-device transfer is not subject to the cap.
- **58% of the log's footprint is index overhead.** Three indexes on one table cost 15.2 MB. Review
  which are actually used (PERF-08/09 ask for a `wifi` index, which would add more); consider dropping
  or consolidating, or moving to a covering index strategy.
- Options, in increasing effort: prune/rotate the log by age (the 6.5-year range suggests most rows
  are historical); drop or consolidate indexes; move the log to a separate database or file with its
  own retention; implement a `BackupAgent` with `onQuotaExceeded` so the app can react instead of
  silently losing the backup. A custom agent could also compress, which the framework does not do.
- The Backup status panel (FEAT-01 deliverable 2) surfaces payload size against the cap, so the user
  at least sees that the backup is not running.

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
