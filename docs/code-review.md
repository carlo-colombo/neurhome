# Code review

Scope: full review of the application source (`app/src/main`, ~6.7k lines of Kotlin).
No code was changed for this review; no build or deploy was performed.

## Correctness / bugs

1. **`logcat` runs in production** — `NeurhomeApplication.onCreate()`
   (`NeurhomeApplication.kt:147-152`) unconditionally spawns
   `Runtime.exec("logcat -f <externalFiles>/app_logcat.txt")`. This is a debug
   artifact: it keeps a process alive writing private launch/context data to
   external storage, consumes battery/space, and should be removed or gated
   behind `BuildConfig.DEBUG`/dev flavor.

2. **Repository coroutine scopes lack a `SupervisorJob`** — `NeurhomeRepository`
   (`NeurhomeRepository.kt:92`) and `FavouritesRepository`
   (`FavouritesRepository.kt:13`) use `CoroutineScope(Dispatchers.Main)`. A single
   uncaught exception in `setTags`/`setAlias`/`toggleVisibility`/`logLaunch`/
   `refreshHomeModel` permanently cancels the scope, so *all* subsequent writes
   silently stop. `SettingsRepository` already does the right thing
   (`SupervisorJob() + Dispatchers.IO`); make the others consistent.

3. **Cursor leak in `exportDatabase`** — `NeurhomeRepository.kt:414` obtains a
   cursor for `pragma wal_checkpoint(full)` and never closes it. Use `.use { ... }`.
   (Also `NeurhomeFileProvider()` is instantiated manually; the provider should be
   injected.)

4. **Non-cancellable, concurrent model training** — `refreshHomeModel()`
   (`NeurhomeRepository.kt:320-334, 363`) cancels the previous job then launches a
   new one, but `PairwiseHomeAppReranker.trainBlocking`
   (`PairwiseHomeAppReranker.kt`) is a tight CPU loop with no
   `ensureActive()`/suspension, so cancellation doesn't interrupt it. Rapid launches
   can queue/overlap full trainings on `Dispatchers.Default`, with last-writer-wins
   on `homeModel`.

5. **Stale alias state** — `ApplicationManagement` uses
   `var alias by remember { mutableStateOf(app.alias) }` (`ApplicationItem.kt:243`)
   without keying on the app. On row reuse or an external alias update, the
   `TextField` shows an old value. Key it on `app.stableListKey()` (as tags already
   are at line 133).

6. **`HomeViewModel.launch` ignores its `query` parameter** —
   `HomeViewModel.kt:342-345` always passes `this.query.value` and discards the
   argument. Either remove the parameter or honor it.

7. **Dead/misleading DAO API** — `ApplicationLogEntryDao.resetAndImport`
   (`ApplicationLogEntryDao.kt:20-23`) is named like an import-replace but never
   calls `cleanUp()`; `cleanUp()` itself has no callers. `insertAll` is only used by
   that dead method. Remove or fix.

8. **Locale-dependent sorting inconsistency** — `applicationAndContacts` sorts with
   `it.label.lowercase()` (default locale), while `applicationsWithTags` and
   `filterApplicationsForTag` use `String.CASE_INSENSITIVE_ORDER`. Under Turkish
   locale this diverges; use `CASE_INSENSITIVE_ORDER` (or `lowercase(Locale.ROOT)`)
   consistently. Same for `tagNamesByApplication` (`String::lowercase`).

## Performance

1. **Model is trained on every launch, even in CLASSIC mode** — `logLaunch` calls
   `refreshHomeModel()` (`NeurhomeRepository.kt:363`), and `init` calls it too
   (`:334`). It reads `applicationLogEntryDao.all()` (unbounded; the sample DB has
   ~106k rows) and runs O(launches × labels) training. Gate it on
   `homeAppSelection == LEARNED`, debounce/throttle, and keep it off the write
   path. This is the single biggest battery/CPU cost.

2. **Heavy SQL polled every 30 s, with no `LIMIT`** — `topAppsByScore()` /
   `simulatedTopApps()` (`ApplicationLogEntryDao.kt`) use non-sargable
   `min(abs(t - now.minuteOfTheDay), …)` in `WHERE`, self-joins, and return *all*
   matching rows while Kotlin only takes 6 (`NeurhomeRepository.kt:290-311`). Add
   `LIMIT` (and ideally a `now`-independent precomputed score). The LEARNED path
   also runs `mostUsedApps()` (full 4-month scan) every tick (`:296`) in addition
   to `topAppsByScore()`.

3. **Cold `applicationsWithTags` recomputed per tag page** — it's a cold `combine`
   (`NeurhomeRepository.kt:192-198`); every `TagScreen` subscription re-maps/sorts
   all apps. `shareIn` it once (or derive all tag lists from one shared list).

4. **Ktor `HttpClient` leak per HomeViewModel** — `AppViewModelProvider` builds
   `WeatherRepository(WeatherServiceImpl())` inside the `HomeViewModel` initializer
   (`AppViewModelProvider.kt:24`), so each navigation back to Home creates a new CIO
   `HttpClient` (thread pools) that is never closed. Make the service an app
   singleton (or close in `onCleared`).

5. **Alias persisted on every keystroke** — `ApplicationItem.kt` `TextField` calls
   `appActions.setAlias(app, it)` per character → one DB upsert per keystroke.
   Persist on collapse/focus loss or debounce.

6. **Glide used only for in-memory `Drawable`s** — `GlideImage(model = app.icon …)`
   in `ApplicationItem`, `BottomBar`, `AppStatisticsScreen`, yet `app.icon` is
   already a `Drawable` from `LauncherIconCache`. Glide adds a dependency plus
   resizing/thread overhead for no benefit. The project already depends on
   `accompanist-drawablePainter` (currently unused) — switch to
   `rememberDrawablePainter` and drop Glide/`glide-ksp`/`GalleryModule`.

7. **Binder lookups on refresh** — `getTopAppsOnce` calls
   `applicationService.toApplication` (PackageManager/LauncherApps) for candidates
   every 30 s, and `quietModes` does per-profile `isQuietModeEnabled` binder calls
   each tick. Cache profiles/quiet modes.

8. **`collectAsState` vs `collectAsStateWithLifecycle`** — `HomeScreen`,
   `AllApplicationsScreen`, `SettingsScreen`, `AppStatisticsScreen` use
   `collectAsState()`; category screens use the lifecycle variant. For a launcher
   this may be intentional, but it's inconsistent and keeps heavy flows alive while
   stopped.

## Refactoring opportunities

- **Repositories depend on Android/Context directly** — `NeurhomeRepository` takes
  `NeurhomeApplication`, `AppDatabase`, `LauncherApps`, `UserManager`
  (`NeurhomeRepository.kt:80-90`). This makes unit testing the ranking/filtering
  logic hard; extract data sources/interfaces so the domain logic is testable on the
  JVM.
- **`homeUIState` is a 7-flow `combine` chain** (`HomeViewModel.kt`) producing five
  intermediate `HomeUIState` copies; consolidate into fewer combines or a single
  state holder.
- **Mutable state field in immutable UI state** — `FilteredUIState.apps` is a `var`
  (`HomeViewModel.kt`); make it `val`. The class also mixes `loading` defaults that
  don't reflect actual loading.
- **`AppActions` grab-bag** — `INeurhomeViewModel.AppActions` bundles 6 unrelated
  callbacks; splitting it improves testability and avoids recomposing unrelated
  rows.
- **Duplicated SQL** — `topAppsByScore` and `simulatedTopApps` share ~90% of their
  CTE; extract shared SQL constants/views. The `ORDER BY` inside the `scores` CTE is
  redundant with the outer order.
- **`SettingsRepository.toggleSetting`** relies on `INSERT` throwing
  `SQLiteConstraintException` to implement a toggle (`SettingsRepository.kt:61-75`).
  Replace with read-then-upsert for clarity and race-safety.
- **`AppViewModelProvider` duplicates wiring** for every VM; consider Hilt/Koin or a
  small composition-root module.
- **Encapsulate `NeurhomeApplication` state** — `cb`, `wifiContext`,
  `wifiLoggingEnabled`, `positionLoggingEnabled` are `internal var`/`@Volatile` and
  mutated from extensions; a small state holder would reduce coupling.
- **`ApplicationService.profiles`** is snapshotted in the constructor; it goes stale
  when work profiles are added/removed. Re-read from `LauncherApps` or register a
  callback.
- **Stale `app/app/schemas/` empty directory** — leftover; remove it. (`app/schemas`
  is the tracked one.)
- **`MainActivity.restart()`** kills the process with `exitProcess(0)`; prefer
  `recreate()` or a proper relaunch intent.

## Test coverage gaps

- No JVM tests for `NeurhomeRepository` ranking selection/filtering,
  `HomeViewModel` query filtering, or the heavy DAO SQL (the riskiest, most complex
  code). The reranker has good parity tests but they're fixture-gated and skipped
  normally.
- No tests around `WeatherServiceImpl` lifecycle, the new `wifiState` capture path,
  or `TagRepository` position invariants under create/move/delete races (only
  `TagRepositoryTest` covers basics).
- CI builds only — per `AGENTS.md` it does not run tests or lint, so regressions in
  the above are only caught locally.

## Suggested priority

1. Remove production `logcat` (Bugs 1).
2. Add `SupervisorJob` to repository scopes (Bugs 2).
3. Gate/debounce model training on LEARNED + `LIMIT` the SQL (Perf 1-2).
4. Fix the HttpClient leak and cursor leak (Perf 4, Bugs 3).
5. Replace Glide with `rememberDrawablePainter` and remove dead DAO/provider code
   (Refactoring).
