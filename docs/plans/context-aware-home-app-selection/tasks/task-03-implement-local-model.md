# Task 3: Implement the Local Learned Model

## Status (2026-09-30): complete; chronological parity passed

The initial linear/neural classifiers and rare-label-preserving fallback did
not pass the original guard. Product requirements were then clarified: rare apps
may be absent from Home recommendations because the full application list
remains available. The follow-up is an offline pairwise residual reranker that
learns preferences only among labels with at least 10 prior launches and may
omit lower-support labels from recommendations.

The deterministic 96-configuration sweep selected `1y/k12/e1/lr0.03/l2=0.0001`.
Frequent-label metrics were **0.643 / 0.420** versus frequent-only classic
**0.647 / 0.437** on fold 1, and **0.610 / 0.449** versus **0.574 / 0.403** on
fold 2. It passes the 0.020 gate on both folds; fold 1 is a narrow MRR
non-regression, while fold 2 improves both metrics. See the
[pairwise reranker report](../pairwise-reranker-report.md).

The isolated Kotlin feature encoder, four-month SQL-history replay factory, and
trainer/ranker are implemented under `data/ml/`. Training runs on
`Dispatchers.Default`; synthetic JVM tests cover deterministic learning,
profile-aware labels, low-support omission, SQL candidate reconstruction, and
context masking. Chronological parity with the offline prototype passed via the
ephemeral, identifier-free fixture in
`PairwiseHomeAppChronologicalParityTest`: fold 1 **0.643 / 0.420** (identical to
the prototype) and fold 2 **0.608 / 0.448** versus **0.610 / 0.449**, within
the 0.003 tolerance and the 0.020 non-regression guard on both folds. Task 4 is
unblocked; classic remains the default ranking until Task 5 ships.

The selected values configure training, not app-specific weights: history
window 365 days, candidate pool 12, one epoch, learning rate 0.03, L2 0.0001,
minimum support 10, seed 1701. The trainer learns per-label context weights,
biases, and a classic-rank residual coefficient from local examples; the
coefficients are rebuilt locally and are not committed or uploaded. The scoring
formula and parameter mapping are documented in the
[reranker report](../pairwise-reranker-report.md).
See the [Task 3 handoff](../task-03-handoff.md) and
[initial classifier report](../model-selection-report.md).

## Objective

Implement the pairwise residual ranker selected by the offline sweep so learned
parameters—not developer-set similarity weights—learn which classic SQL
candidates are preferred in each context. Finish Task 3 by verifying the
Kotlin implementation on the same chronological folds.

## Scope

- Use each recorded launch as a supervised example: context is input;
  package/profile is the target.
- Encode local time cyclically, day of week, coarsened location, and Wi-Fi
  context. Unknown context must be masked/omitted, not encoded as a mismatch or
  as `NO_WIFI`.
- Rerank only positive-score classic SQL candidates with at least 10 prior
  launches. Lower-support apps may be absent from recommendations but must
  remain accessible from the full application list.
- Train and infer entirely on-device. Apply updates off the UI thread; use
  replay or periodic retraining to avoid overreacting to a single launch.
- Persist a versioned local checkpoint if the selected runtime needs one. It
  must be rebuildable from local launch history and must not be uploaded.
- Do not add a cloud API or network dependency for training/inference.

## Acceptance criteria

- Synthetic tests show that changing learned launch examples changes the
  predicted ranking in the expected direction without hand-authored context
  score weights.
- Tests cover profiles, app-label growth, unknown Wi-Fi, known `NO_WIFI`,
  missing location, empty/low-volume history, deterministic learning, and
  persisted model versioning if a checkpoint is introduced. Rare-label ranking
  is diagnostic, not a pass/fail criterion.
- Training and prediction are deterministic for a fixed model/version and do
  not block the main thread.

## Dependencies and verification

- Depends on the selected pairwise reranker and Task 2's event semantics; both
  are complete. The outstanding acceptance check is chronological parity for
  the Kotlin implementation against the Python offline result.
