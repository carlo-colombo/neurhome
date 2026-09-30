# Task 3 handoff: local model implementation gate

This handoff combines the Task 1 backtest decision with Task 2's completed event
semantics. It is intended to be read before implementing
[`tasks/task-03-implement-local-model.md`](tasks/task-03-implement-local-model.md).

## Decision from Task 1

**No learned classifier or training horizon was selected in the initial
comparison.** Keep the current four-calendar-month Home SQL ranking in
production. The original rare-label guard has since been superseded by the
frequent-label gate in the product clarification below; this is a measured
no-go, not permission to choose the best aggregate score by intuition.

The full aggregate audit and model comparison are in
[`model-selection-report.md`](model-selection-report.md). Key evidence:

- History contained **106,084** launches, **348** app/profile labels, and two
  profiles. Null historical Wi-Fi values were treated as **unknown**.
- The development fold used the earliest 60% for training and the next 20% for
  testing. No tested linear or neural candidate stayed within the 0.020
  non-regression guard for both overall and rare-label HitRate@6 and MRR.
- On that fold, classic scored **0.556 / 0.367** overall and **0.357 / 0.190**
  for rare labels (HitRate@6 / MRR); learned candidates had **0.000–0.001** rare
  HitRate@6.
- In the final chronological fold, the neural one-year candidate improved
  overall to **0.524 / 0.385**, versus classic **0.516 / 0.354**, but its rare
  results were **0.000 / 0.003**, versus classic **0.282 / 0.123**. Overall gains
  therefore do not justify replacing the existing ranking.
- No learned runtime or horizon is selected. The measured horizon variants
  (full, two years, one year, one-year exponential decay) all showed the same
  rare-label problem.

## Task 2 data contract (completed)

Task 2 is committed as `19dfaf5` (`Capture explicit launch context`); Room is
version 20. Relevant source types are `WifiContext` / `WifiContextState` and
`ApplicationLogEntry.wifiState`:

- Wi-Fi state is `CONNECTED`, `NO_WIFI`, or `UNKNOWN`. Historical v19 rows
  migrate to `UNKNOWN`; nullable historical `wifi` is not evidence of
  disconnection. The SSID is retained only for a connected context.
- Launch labels remain `(packageName, user/profile)`. Use the stored launch
  context, not the model-training machine's current context.
- Location is stored locally and exposed to model features as a coarse
  geohash. Missing location means the location feature is absent.
- Preserve the existing position-logging setting and permission gates. Do not
  add background capture, upload, or Usage Access.

## Product clarification and revised implementation gate (2026-09-30)

The Home list is a shortlist; users can still open every installed app from the
full application list. The user has clarified that rare apps may be omitted from
recommendations. Therefore rare-label preservation is **not** a release gate.
The initial Task 3 classifier/fallback results below remain valid historical
measurements, but their all-plus-rare acceptance rule is superseded by this
frequent-label gate.

1. Keep the classifier/reranker isolated, deterministic, and local. Do not
   change Home's selected/default ranking or Settings integration during the
   offline experiment.
2. Train a pairwise residual reranker over the current SQL candidate order.
   Only labels with at least **10 prior training launches** can be reranked;
   lower-support labels may be absent from the recommendation list. They remain
   launchable through the full application list. Do not use developer-authored
   context similarity weights.
3. Compare against both current SQL and SQL filtered to labels with at least 10
   prior training launches. Tune/select on fold 1 only; use fold 2 only for
   later-period verification. Require frequent-label HitRate@6 and MRR to be
   within **0.020 absolute** of the frequent-only SQL baseline on both folds.
   Report overall and rare-label metrics as diagnostics.
4. If no candidate passes, keep classic as production and report Task 3 blocked
   on frequent-label ranking quality. Do not relax the frequent-label guard
   based on fold 2 or aggregate score alone.
5. Automate deterministic tuning of horizon, candidate count, epochs, learning
   rate, and regularization. Tests use synthetic/anonymized examples and cover
   determinism, profile-aware labels, label growth, `UNKNOWN` Wi-Fi, known
   `NO_WIFI`, missing location, and low-volume history. Keep any eventual
   on-device training off the UI thread and all data local.

## Initial classifier/fallback outcome (historical, 2026-09-30)

**No classifier/runtime/horizon was selected under the original all-plus-rare
gate.** The first offline harness supported v20 `wifiState`: known `NO_WIFI` is
distinct, `UNKNOWN` is omitted, and legacy retained SSIDs remain connected
evidence. The export used for that initial audit had **0 known `NO_WIFI`** rows,
so that distinction was covered synthetically but not validated by that
backtest.

The evaluated fallback preserves each positive-score SQL item with fewer than
10 prior training launches in its exact rank slot, replaces only frequent-label
SQL slots with learned frequent-label order, and appends remaining learned
frequent labels. It is target-independent and preserved classic rare-label
HitRate@6 / MRR exactly on both folds:

- Fold 1 classic: **0.556 / 0.367 overall**, **0.610 / 0.415 frequent**,
  **0.357 / 0.190 rare**. Best fallback by fold-1 HitRate@6 then MRR was
  neural/decay-1y at **0.431 / 0.220 overall**, **0.451 / 0.228 frequent**,
  **0.357 / 0.190 rare**. Its overall deficit is **0.125 HitRate@6 / 0.147
  MRR**, so it fails the unchanged 0.020 gate.
- Fold 2 classic: **0.516 / 0.354 overall**, **0.557 / 0.394 frequent**,
  **0.282 / 0.123 rare**. The fold-1 leader scored **0.523 / 0.335 overall**,
  **0.564 / 0.371 frequent**, **0.282 / 0.123 rare**. No fold-1 candidate
  qualified for selection; no later-fold gain can waive that failure.
- All eight model/horizon-plus-fallback results are recorded in
  [`model-selection-report.md`](model-selection-report.md).

## Pairwise reranker follow-up (2026-09-30)

The product-approved 96-configuration sweep tunes only on fold 1 and verifies
on fold 2. It selected `1y/k12/e1/lr0.03/l2=0.0001` against the SQL candidate
list filtered to app/profile labels with at least 10 prior launches:

- Fold 1 frequent-only SQL: **0.647 / 0.437**; reranker: **0.643 / 0.420**.
  Deltas are **-0.004 HitRate@6 / -0.017 MRR**, within the 0.020 guard.
- Fold 2 frequent-only SQL: **0.574 / 0.403**; reranker: **0.610 / 0.449**.
  Deltas are **+0.036 / +0.046**. This fold was not used for tuning.
- The reranker omits labels below the support threshold. Rare metrics are zero
  by design and are not a release gate; all apps remain available from the full
  application list.

These are training hyperparameters, not fixed app scores. The Kotlin config
uses a 365-day window, 12 candidates, one epoch, learning rate 0.03, L2 0.0001,
support 10, and seed 1701. Per-label context coefficients and rank residual
weights are learned from local examples; the scoring formula is in the
[reranker report](pairwise-reranker-report.md).

This is an offline-prototype pass, not yet a Home production selection. The
Kotlin feature encoder, history replay factory, and train/inference component
are implemented, and chronological parity against the offline prototype has now
passed.

No Kotlin model, checkpoint, Settings selector, or Home integration was
implemented as part of the original classifier experiment. The follow-up
pairwise reranker passes the revised frequent-label gate in the offline
prototype; an isolated Kotlin encoder, historical candidate-replay factory, and
trainer/ranker now exist. Keep the current four-calendar-month SQL Home ranking
in production until Task 5 integrates the verified model.
## Kotlin chronological parity (2026-09-30, passed)

`PairwiseHomeAppChronologicalParityTest` runs the full local corpus when
`HOME_APP_RERANKER_PARITY_FIXTURE` points at the anonymized export produced by
`tools/evaluate_home_app_reranker.py --kotlin-parity-fixture`; without the
environment variable the test is skipped, so no personal data is needed for
normal test runs. The export replaces package/profile labels with integer IDs
and writes only hashed feature IDs and numeric context—no package names,
SSIDs, coordinates, geohashes, or raw timestamps beyond local wall-clock
strings—and is written outside the repository.

Result: **parity passed**. Frequent-label HitRate@6 / MRR was **0.643 / 0.420**
on fold 1 (identical to the prototype) and **0.608 / 0.448** versus
**0.610 / 0.449** on fold 2, within the 0.003 parity tolerance and the 0.020
non-regression guard against the frequent-only SQL baseline on both folds
(fold 1 baseline 0.647 / 0.437; fold 2 baseline 0.574 / 0.403). The
double-precision Kotlin SQL replay diverged from the float32 Python replay on
2,953 of 66,842 training events (19 missing, 15 extra, 2,919 candidate-set
boundary slots; 0 support-count differences) plus tie-order-only differences on
14,193; this is diagnostic only and did not move either fold's metrics.

The experiment used a v19 export, so it cannot validate known `NO_WIFI`, which
remains covered only by synthetic tests until a v20 export with explicit
observations is available. Details are in
[`pairwise-reranker-report.md`](pairwise-reranker-report.md).

Original verification was `python3 -m unittest discover -s tools/tests` (7
tests), `:app:assembleDevDebug`, and an attached-device smoke test. For the
reranker follow-up, the Python suite passes (13 tests),
`:app:testDevDebugUnitTest :app:assembleDevDebug` passes (24 JVM tests), and the
attached-device list was empty, so no APK could be deployed. The parity
follow-up adds the fixture-gated `PairwiseHomeAppChronologicalParityTest`
(25 JVM tests with the fixture set; skipped otherwise); the attached-device
list was again empty. No commit was made;
the local database was opened read-only and no identifiers or context values
were written to reports.

The initial classifier harness is `tools/evaluate_home_app_selection.py`; the
pairwise reranker and automatic fold-1 sweep are in
`tools/evaluate_home_app_reranker.py`. Reproduction commands and the unchanged
NumPy dependency are documented in their reports. The personal database is not
a build input and must remain uncommitted.
