# Code Review Remediation — Task Index

Individual, PR-sized tasks derived from [`docs/code-review.md`](../../code-review.md).
No code was changed while producing this list.

**Excluded by request:** Performance item 8 (`collectAsState` vs
`collectAsStateWithLifecycle`) — lifecycle collection was intentionally avoided
because it causes UI slowdowns. Do not create or execute a task for it.

## Progress

- **Done:** tasks 1–28. Every remediation task is implemented, independently
  reviewed (a fresh-context `reviewer` per lane), and verified on the `edge70`
  emulator. Task 23 was a verified no-op: the stale `app/app/schemas/` directory
  was never git-tracked (only empty, untracked directories existed), and those
  leftover directories were removed without touching the tracked `app/schemas`.
  Task 28 was added from a dedicated test-gap audit.
- **Pending:** none.
- **Verification:** `:app:testDevDebugUnitTest` (full suite), `:app:assembleDevDebug`,
  `:app:lintDevDebug` (0 errors) and `:app:connectedDevDebugAndroidTest`
  (27/27 on the `edge70` emulator) all pass.
- **Baseline:** tasks 1, 2, 3, 4, 5, 9, 10, 12 were completed before this pass.

## Task list

| # | Task | Review item | Priority | Status |
|---|------|-------------|----------|--------|
| 1 | [Remove/gate production `logcat`](tasks/task-01-remove-production-logcat.md) | Bugs 1 | P0 | Done |
| 2 | [Add `SupervisorJob` to repository scopes](tasks/task-02-supervisor-job-repository-scopes.md) | Bugs 2 | P0 | Done |
| 3 | [Fix cursor leak in `exportDatabase`](tasks/task-03-cursor-leak-exportdatabase.md) | Bugs 3 | P1 | Done |
| 4 | [Make model training cancellable](tasks/task-04-cancellable-model-training.md) | Bugs 4 | P1 | Done |
| 5 | [Key alias state on app identity](tasks/task-05-alias-state-keying.md) | Bugs 5 | P1 | Done |
| 6 | [Honor/remove `HomeViewModel.launch` query](tasks/task-06-homeviewmodel-launch-query.md) | Bugs 6 | P2 | Done |
| 7 | [Remove dead DAO API](tasks/task-07-remove-dead-dao-api.md) | Bugs 7 | P2 | Done |
| 8 | [Locale-independent sorting](tasks/task-08-locale-independent-sorting.md) | Bugs 8 | P2 | Done |
| 9 | [Gate/debounce training on `LEARNED`](tasks/task-09-gate-debounce-training.md) | Perf 1 | P0 | Done |
| 10 | [Add `LIMIT`/precomputed score to top-apps SQL](tasks/task-10-limit-top-apps-sql.md) | Perf 2 | P1 | Done |
| 11 | [Share `applicationsWithTags` flow](tasks/task-11-share-applications-with-tags.md) | Perf 3 | P2 | Done |
| 12 | [Weather `HttpClient` app singleton](tasks/task-12-httpclient-singleton.md) | Perf 4 | P1 | Done |
| 13 | [Debounce alias persistence](tasks/task-13-debounce-alias-persistence.md) | Perf 5 | P2 | Done |
| 14 | [Replace Glide with `rememberDrawablePainter`](tasks/task-14-replace-glide-drawablepainter.md) | Perf 6 | P2 | Done |
| 15 | [Cache profiles/quiet-mode lookups](tasks/task-15-cache-binder-lookups.md) | Perf 7 | P2 | Done |
| 16 | [Extract repository data sources](tasks/task-16-extract-repository-data-sources.md) | Refactoring | P3 | Done |
| 17 | [Consolidate Home ViewModel state](tasks/task-17-consolidate-home-viewmodel-state.md) | Refactoring | P3 | Done |
| 18 | [Split `AppActions`](tasks/task-18-split-app-actions.md) | Refactoring | P3 | Done |
| 19 | [Deduplicate top-apps SQL](tasks/task-19-deduplicate-top-apps-sql.md) | Refactoring | P3 | Done |
| 20 | [Rewrite `toggleSetting` read-then-upsert](tasks/task-20-toggle-setting-read-upsert.md) | Refactoring | P3 | Done |
| 21 | [Consolidate ViewModel wiring](tasks/task-21-consolidate-viewmodel-wiring.md) | Refactoring | P3 | Done |
| 22 | [Encapsulate application state + refresh profiles](tasks/task-22-encapsulate-application-state.md) | Refactoring | P3 | Done |
| 23 | [Remove stale `app/app/schemas/` dir](tasks/task-23-remove-stale-schemas-dir.md) | Refactoring | P3 | Done (no-op) |
| 24 | [Replace `MainActivity.restart()` `exitProcess`](tasks/task-24-mainactivity-restart.md) | Refactoring | P3 | Done |
| 25 | [JVM tests: ranking, filtering, DAO SQL](tasks/task-25-jvm-tests-ranking-filtering-dao.md) | Tests | P2 | Done |
| 26 | [Tests: weather, Wi-Fi capture, tag races](tasks/task-26-tests-weather-wifi-tag-races.md) | Tests | P3 | Done |
| 27 | [Run tests and lint in CI](tasks/task-27-ci-run-tests-and-lint.md) | Tests/CI | P2 | Done |
| 28 | [JVM tests: solar, weather mapping, Event, ML parsing](tasks/task-28-jvm-tests-solar-weather-models-ml.md) | Test-gap audit | P2 | Done |

## Suggested execution order

All of the above are complete. Work was executed in file-disjoint waves of at
most three concurrent lanes (one git worktree per task), following the priority
order: P0/P1 → remaining bugs → performance → refactoring → tests/CI.

## Verification for every task

- `./gradlew :app:testDevDebugUnitTest` when logic changes.
- `./gradlew :app:assembleDevDebug`, then deploy and launch per `AGENTS.md`
  (physical device when connected, otherwise the `edge70` emulator).
- Commit any newly exported Room schema when entities/version change.
