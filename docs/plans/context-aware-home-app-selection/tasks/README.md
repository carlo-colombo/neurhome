# Context-Aware Home App Selection — Implementation Tasks

These are individual implementation tasks derived from
[`context-aware-home-app-selection.md`](../../context-aware-home-app-selection.md).
Complete them in order unless noted otherwise. Task 1 records a measured model
choice or no-go from a chronological backtest; do not ship fixed, hand-weighted
similarity scoring as a substitute for learned parameters.

**Current gate:** rare-label coverage is not required because all installed
apps remain accessible from the full application list. Tasks 3–6 are complete.
Task 3 is complete: the
offline reranker passed the revised frequent-label gate, and the Kotlin
implementation passed chronological parity (fold 1 **0.643 / 0.420** matching
the prototype, fold 2 **0.608 / 0.448** versus **0.610 / 0.449**, both within
the 0.003 parity tolerance and the 0.020 non-regression guard). Task 4 is also
complete: Settings persists a `home.app.selection` choice (`CLASSIC` default,
`LEARNED` gated on both loggers plus the location permission) without a database
migration. The next task is [Task 5](task-05-integrate-home-ranking.md), which
consumes the selection in Home; classic remains the default ranking until then.
The selection is consumed by Home; classic remains the default ranking. Task 6
verification passed JVM tests, Python evaluator tests, the dev build, and all
12 connected Android tests including v19 migration. See the [Task 3 handoff](../task-03-handoff.md)
and [reranker evaluation](../pairwise-reranker-report.md).

## Task order

1. [Audit history and select a model](task-01-audit-history-and-select-model.md)
2. [Capture explicit context](task-02-capture-context.md)
3. [Implement local learning](task-03-implement-local-model.md)
4. [Add the Settings selector](task-04-settings-selector.md)
5. [Integrate Home predictions](task-05-integrate-home-ranking.md)
6. [Verify end-to-end behavior](task-06-end-to-end-verification.md)

The sample database is personal usage data. Keep the local export out of source
control and do not include raw timestamps, package names, SSIDs, or coordinates
in committed reports or test fixtures.
