# Task 1: Audit History and Select the Model

The initial classifier comparison recorded a no-go. The follow-up pairwise
reranker sweep selected an offline configuration under the revised
frequent-label gate; production selection awaits chronological parity with the
Kotlin implementation. See the
[pairwise reranker report](../pairwise-reranker-report.md).

## Objective

Use the available launch history to choose a locally trainable model and
training horizon based on measured future-launch predictions, not intuition.

## Scope

- Profile history counts, date range, profile/app frequency, and context
  coverage. Treat legacy `wifi IS NULL` as unknown, not as a no-Wi-Fi example.
- Implement a repeatable offline evaluation harness that compares the current
  Home SQL ranking with at least a learned linear classifier and a compact
  neural classifier, if the chosen implementation can be evaluated reliably.
- Use chronological/walk-forward splits: train on earlier launches and test on
  later launches. Never randomly split individual rows across train and test.
- Compare candidate history horizons (full history and shorter/decayed history)
  to account for changing habits.
- Measure at least HitRate@6 and reciprocal rank of the next launched app;
  report results for frequent and rare app/profile labels separately.
- Keep evaluation output aggregate and free of raw app, SSID, or location data.

## Acceptance criteria

- A short decision report records sample size, feature coverage, temporal split,
  metrics, and either a selected model/runtime/training horizon or a measured
  no-go.
- Model selection uses frequent-label HitRate@6 and MRR against classic SQL
  filtered to frequent candidates. Rare-label and all-launch metrics are
  reported but do not gate release because all apps remain accessible outside
  the recommendation list.
- Tune/select on fold 1 only; verify the chosen configuration on fold 2 without
  retuning. Require both frequent-label metrics to be within 0.020 absolute of
  the frequent-only classic baseline on both folds.
- `neurhome_database_sample.db` and any raw export remain local and are not
  committed. Tests use synthetic or anonymized fixtures.

## Dependencies and verification

- No implementation dependencies. This task precedes model implementation.
- Repeat the evaluation from a clean checkout using documented commands and a
  locally supplied database path; do not make a personal export a build input.
- The initial linear/neural comparison produced a no-go. The pairwise residual
  reranker is the follow-up candidate; its automated parameter sweep and results
  are recorded in [`pairwise-reranker-report.md`](../pairwise-reranker-report.md).
