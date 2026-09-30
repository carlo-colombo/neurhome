# Home app selection: pairwise residual reranker experiment

Aggregate-only output: app/profile identifiers, SSIDs, geohashes, and coordinates are never emitted.

## Product objective and evaluation gate

Rare apps may be absent from the six Home recommendations because the full application list remains available. Therefore model selection is based on frequent-label HitRate@6 and MRR; rare-label and all-launch metrics are diagnostics, not release gates.
The candidate is drawn only from positive-score classic SQL results, and only labels with at least 10 prior launches are eligible for the reranker. The baseline comparison also filters classic to those frequent candidates so rare-label slots do not consume recommendation positions in either arm.
A candidate must be within 0.020 absolute of the frequent-only classic baseline on both frequent-label HitRate@6 and MRR on fold 1 and fold 2. Fold 1 selects/tunes; fold 2 verifies without retuning.

## Method

- Launch history: **106,084** rows, **348** app/profile labels, 2 profiles.
- The local export is Room v19. Wi-Fi context: **64,153** connected, **0** known
  `NO_WIFI`, **41,931** unknown. Unknown is omitted; legacy retained SSIDs count
  as connected evidence.
- Two chronological expanding folds: earliest 60% trains fold 1 and 60–80% tests; earliest 80% trains fold 2 and 80–100% tests.
- At each historical training event, classic SQL candidates are reconstructed using only earlier launches in the rolling four-calendar-month window. Pairwise examples include a target only when it is among the classic candidates and has at least 10 earlier launches; comparisons are only between labels with at least 10 prior launches.
- The pairwise logistic ranker learns per-label context interactions, label bias, and a coefficient for the classic rank signal. Context is the existing deterministic profile/time/day/coarse-location/Wi-Fi encoding. The output can reorder only classic candidates; rare candidates are omitted from recommendations, not removed from the installed-app list.
- Training is deterministic for a fixed seed/configuration. All tuning uses fold 1. There is no checkpoint, Android model, network access, or Home integration in this offline experiment.

## How the selected configuration defines learned weights

The tuple `1y/k12/e1/lr0.03/l2=0.0001` is a **training configuration**, not a
set of fixed app scores. The Kotlin default config maps it to
`historyDays=365`, `candidateCount=12`, `epochs=1`, `learningRate=0.03`, and
`l2=0.0001` (with `minimumSupport=10` and deterministic `seed=1701`). The
trainer uses these settings to fit coefficients from local pairwise examples.

For candidate label `a`, context vector `x`, and zero-based classic position
`r` within the frequent candidate pool, inference scores:

```text
score(a | x, r) = bias[a]
                + sum(categoricalWeight[a, feature] for feature in x)
                + dot(numericWeight[a], numericContext(x))
                + classicRankWeight / (r + 1)
```

Pairwise logistic updates learn the per-label categorical/numeric weights,
label bias, and classic-rank coefficient; the learning rate controls each
update and L2 regularization shrinks active coefficients. No developer-authored
context similarity weights or per-app scores are checked in. The learned arrays
are derived locally from launch history, are not emitted in this report, and
there is no persisted checkpoint yet.

## Automatic parameter sweep

The deterministic grid searched **96** configurations: horizons `full, 2y, 1y, decay-1y`; frequent candidate limits `6, 12, 24`; epochs `1, 2`; learning rates `0.03, 0.1`; L2 values `0.0001, 0.01`. The fold-1 leader is chosen by frequent-label HitRate@6, then MRR; eligible candidates must meet the fold-1 0.020 guard. Fold 2 never tunes parameters.

| Fold | Prior training rows | Train through | Test interval | Scored launches |
|---|---:|---|---|---:|
| fold-1 | 62,188 | 2023-12-24 | 2023-12-24 – 2025-07-17 | 12,265 |
| fold-2 | 74,453 | 2025-07-17 | 2025-07-17 – 2026-09-30 | 10,534 |

## Fold-1 tuning results

Metrics are HitRate@6 / MRR. Overall and rare-label values are reported for visibility; only frequent-label metrics drive tuning/acceptance.

| Candidate | Overall | Frequent | Rare | Pairwise training examples |
|---|---:|---:|---:|---:|
| Current Home SQL | 0.556 / 0.367 (n=12,265) | 0.610 / 0.415 (n=9,651) | 0.357 / 0.190 (n=2,614) | — |
| SQL, frequent candidates only | 0.509 / 0.344 (n=12,265) | 0.647 / 0.437 (n=9,651) | 0.000 / 0.000 (n=2,614) | — |
| `1y/k6/e1/lr0.03/l20.0001` | 0.509 / 0.328 (n=12,265) | 0.647 / 0.417 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `1y/k6/e2/lr0.03/l20.0001` | 0.509 / 0.327 (n=12,265) | 0.647 / 0.416 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `1y/k6/e1/lr0.1/l20.0001` | 0.509 / 0.327 (n=12,265) | 0.647 / 0.415 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `decay-1y/k6/e1/lr0.03/l20.0001` | 0.509 / 0.325 (n=12,265) | 0.647 / 0.413 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `full/k6/e1/lr0.03/l20.0001` | 0.509 / 0.324 (n=12,265) | 0.647 / 0.411 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `1y/k6/e1/lr0.1/l20.01` | 0.509 / 0.324 (n=12,265) | 0.647 / 0.411 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `decay-1y/k6/e2/lr0.03/l20.0001` | 0.509 / 0.324 (n=12,265) | 0.647 / 0.411 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `1y/k6/e2/lr0.1/l20.0001` | 0.509 / 0.324 (n=12,265) | 0.647 / 0.411 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `full/k6/e2/lr0.03/l20.0001` | 0.509 / 0.323 (n=12,265) | 0.647 / 0.411 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `1y/k6/e2/lr0.03/l20.01` | 0.509 / 0.323 (n=12,265) | 0.647 / 0.410 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `2y/k6/e1/lr0.03/l20.0001` | 0.509 / 0.323 (n=12,265) | 0.647 / 0.410 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `1y/k6/e1/lr0.03/l20.01` | 0.509 / 0.322 (n=12,265) | 0.647 / 0.409 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `1y/k6/e2/lr0.1/l20.01` | 0.509 / 0.322 (n=12,265) | 0.647 / 0.409 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `2y/k6/e1/lr0.1/l20.0001` | 0.509 / 0.321 (n=12,265) | 0.647 / 0.408 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `decay-1y/k6/e1/lr0.03/l20.01` | 0.509 / 0.320 (n=12,265) | 0.647 / 0.407 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `decay-1y/k6/e2/lr0.03/l20.01` | 0.509 / 0.320 (n=12,265) | 0.647 / 0.407 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `2y/k6/e2/lr0.03/l20.0001` | 0.509 / 0.320 (n=12,265) | 0.647 / 0.406 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `decay-1y/k6/e1/lr0.1/l20.0001` | 0.509 / 0.319 (n=12,265) | 0.647 / 0.406 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `2y/k6/e1/lr0.03/l20.01` | 0.509 / 0.319 (n=12,265) | 0.647 / 0.406 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `full/k6/e1/lr0.1/l20.0001` | 0.509 / 0.319 (n=12,265) | 0.647 / 0.405 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `full/k6/e2/lr0.03/l20.01` | 0.509 / 0.318 (n=12,265) | 0.647 / 0.404 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `full/k6/e1/lr0.03/l20.01` | 0.509 / 0.317 (n=12,265) | 0.647 / 0.403 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `2y/k6/e1/lr0.1/l20.01` | 0.509 / 0.316 (n=12,265) | 0.647 / 0.402 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `decay-1y/k6/e1/lr0.1/l20.01` | 0.509 / 0.315 (n=12,265) | 0.647 / 0.401 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `2y/k6/e2/lr0.03/l20.01` | 0.509 / 0.315 (n=12,265) | 0.647 / 0.400 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `2y/k6/e2/lr0.1/l20.0001` | 0.509 / 0.314 (n=12,265) | 0.647 / 0.399 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `full/k6/e2/lr0.1/l20.0001` | 0.509 / 0.313 (n=12,265) | 0.647 / 0.398 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `decay-1y/k6/e2/lr0.1/l20.0001` | 0.509 / 0.313 (n=12,265) | 0.647 / 0.398 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `2y/k6/e2/lr0.1/l20.01` | 0.509 / 0.313 (n=12,265) | 0.647 / 0.398 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `full/k6/e1/lr0.1/l20.01` | 0.509 / 0.312 (n=12,265) | 0.647 / 0.397 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `decay-1y/k6/e2/lr0.1/l20.01` | 0.509 / 0.312 (n=12,265) | 0.647 / 0.396 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `full/k6/e2/lr0.1/l20.01` | 0.509 / 0.305 (n=12,265) | 0.647 / 0.388 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `2y/k12/e1/lr0.03/l20.0001` | 0.509 / 0.326 (n=12,265) | 0.646 / 0.414 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `full/k12/e1/lr0.03/l20.0001` | 0.509 / 0.316 (n=12,265) | 0.646 / 0.402 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `2y/k12/e2/lr0.03/l20.0001` | 0.508 / 0.322 (n=12,265) | 0.646 / 0.409 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `2y/k24/e2/lr0.03/l20.0001` | 0.506 / 0.324 (n=12,265) | 0.643 / 0.411 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `1y/k12/e1/lr0.03/l20.0001` | 0.506 / 0.331 (n=12,265) | 0.643 / 0.420 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `1y/k12/e2/lr0.03/l20.0001` | 0.505 / 0.329 (n=12,265) | 0.642 / 0.418 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `full/k24/e1/lr0.03/l20.0001` | 0.505 / 0.316 (n=12,265) | 0.642 / 0.401 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `1y/k12/e1/lr0.1/l20.0001` | 0.505 / 0.328 (n=12,265) | 0.642 / 0.417 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `2y/k24/e1/lr0.03/l20.0001` | 0.505 / 0.329 (n=12,265) | 0.641 / 0.418 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `1y/k24/e2/lr0.03/l20.0001` | 0.504 / 0.330 (n=12,265) | 0.641 / 0.419 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `2y/k24/e1/lr0.1/l20.0001` | 0.504 / 0.326 (n=12,265) | 0.640 / 0.414 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `full/k12/e2/lr0.03/l20.0001` | 0.503 / 0.318 (n=12,265) | 0.640 / 0.405 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `1y/k24/e2/lr0.1/l20.0001` | 0.503 / 0.324 (n=12,265) | 0.639 / 0.412 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `2y/k12/e1/lr0.1/l20.0001` | 0.502 / 0.321 (n=12,265) | 0.638 / 0.409 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `decay-1y/k12/e1/lr0.03/l20.0001` | 0.502 / 0.321 (n=12,265) | 0.638 / 0.408 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `1y/k12/e2/lr0.1/l20.0001` | 0.502 / 0.324 (n=12,265) | 0.638 / 0.411 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `1y/k12/e2/lr0.03/l20.01` | 0.502 / 0.322 (n=12,265) | 0.638 / 0.410 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `2y/k24/e1/lr0.03/l20.01` | 0.502 / 0.323 (n=12,265) | 0.638 / 0.411 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `1y/k12/e2/lr0.1/l20.01` | 0.501 / 0.321 (n=12,265) | 0.637 / 0.408 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `1y/k24/e1/lr0.03/l20.0001` | 0.501 / 0.330 (n=12,265) | 0.637 / 0.420 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `2y/k24/e1/lr0.1/l20.01` | 0.501 / 0.323 (n=12,265) | 0.636 / 0.410 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `1y/k12/e1/lr0.03/l20.01` | 0.500 / 0.323 (n=12,265) | 0.635 / 0.411 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `2y/k12/e1/lr0.03/l20.01` | 0.499 / 0.321 (n=12,265) | 0.634 / 0.408 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `decay-1y/k24/e1/lr0.03/l20.0001` | 0.499 / 0.319 (n=12,265) | 0.634 / 0.406 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `1y/k24/e1/lr0.1/l20.0001` | 0.499 / 0.327 (n=12,265) | 0.634 / 0.416 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `full/k24/e2/lr0.03/l20.0001` | 0.498 / 0.315 (n=12,265) | 0.633 / 0.400 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `decay-1y/k12/e2/lr0.03/l20.0001` | 0.497 / 0.320 (n=12,265) | 0.632 / 0.407 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `full/k12/e2/lr0.1/l20.0001` | 0.497 / 0.313 (n=12,265) | 0.631 / 0.398 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `full/k12/e1/lr0.1/l20.0001` | 0.497 / 0.313 (n=12,265) | 0.631 / 0.398 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `1y/k12/e1/lr0.1/l20.01` | 0.496 / 0.320 (n=12,265) | 0.631 / 0.406 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `1y/k24/e2/lr0.03/l20.01` | 0.496 / 0.322 (n=12,265) | 0.630 / 0.409 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `full/k24/e1/lr0.03/l20.01` | 0.496 / 0.313 (n=12,265) | 0.630 / 0.397 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `full/k24/e1/lr0.1/l20.0001` | 0.496 / 0.311 (n=12,265) | 0.630 / 0.396 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `2y/k12/e1/lr0.1/l20.01` | 0.495 / 0.318 (n=12,265) | 0.629 / 0.405 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `2y/k12/e2/lr0.03/l20.01` | 0.495 / 0.317 (n=12,265) | 0.629 / 0.403 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `1y/k24/e2/lr0.1/l20.01` | 0.495 / 0.320 (n=12,265) | 0.629 / 0.407 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `2y/k24/e2/lr0.03/l20.01` | 0.495 / 0.319 (n=12,265) | 0.629 / 0.406 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `decay-1y/k24/e2/lr0.03/l20.0001` | 0.495 / 0.319 (n=12,265) | 0.629 / 0.405 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `1y/k24/e1/lr0.03/l20.01` | 0.494 / 0.322 (n=12,265) | 0.628 / 0.410 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `full/k12/e1/lr0.03/l20.01` | 0.494 / 0.312 (n=12,265) | 0.628 / 0.396 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `decay-1y/k12/e1/lr0.03/l20.01` | 0.492 / 0.316 (n=12,265) | 0.626 / 0.401 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `decay-1y/k12/e1/lr0.1/l20.0001` | 0.491 / 0.316 (n=12,265) | 0.625 / 0.402 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `2y/k12/e2/lr0.1/l20.0001` | 0.491 / 0.306 (n=12,265) | 0.624 / 0.389 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `full/k12/e2/lr0.03/l20.01` | 0.490 / 0.315 (n=12,265) | 0.623 / 0.400 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `decay-1y/k12/e2/lr0.1/l20.0001` | 0.489 / 0.313 (n=12,265) | 0.621 / 0.398 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `decay-1y/k12/e2/lr0.03/l20.01` | 0.488 / 0.315 (n=12,265) | 0.620 / 0.401 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `2y/k24/e2/lr0.1/l20.0001` | 0.488 / 0.309 (n=12,265) | 0.620 / 0.393 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `full/k24/e1/lr0.1/l20.01` | 0.487 / 0.309 (n=12,265) | 0.619 / 0.393 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `decay-1y/k24/e1/lr0.1/l20.0001` | 0.487 / 0.313 (n=12,265) | 0.618 / 0.398 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `1y/k24/e1/lr0.1/l20.01` | 0.486 / 0.318 (n=12,265) | 0.618 / 0.405 (n=9,651) | 0.000 / 0.000 (n=2,614) | 11,508 / 57,183 |
| `decay-1y/k24/e1/lr0.03/l20.01` | 0.486 / 0.314 (n=12,265) | 0.618 / 0.399 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `full/k24/e2/lr0.03/l20.01` | 0.486 / 0.313 (n=12,265) | 0.618 / 0.397 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `full/k24/e2/lr0.1/l20.0001` | 0.485 / 0.312 (n=12,265) | 0.616 / 0.396 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `full/k12/e2/lr0.1/l20.01` | 0.483 / 0.307 (n=12,265) | 0.614 / 0.390 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `full/k12/e1/lr0.1/l20.01` | 0.483 / 0.309 (n=12,265) | 0.613 / 0.392 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `2y/k12/e2/lr0.1/l20.01` | 0.481 / 0.308 (n=12,265) | 0.612 / 0.391 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `decay-1y/k12/e2/lr0.1/l20.01` | 0.481 / 0.309 (n=12,265) | 0.611 / 0.393 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `decay-1y/k24/e2/lr0.03/l20.01` | 0.479 / 0.313 (n=12,265) | 0.609 / 0.398 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `decay-1y/k12/e1/lr0.1/l20.01` | 0.478 / 0.311 (n=12,265) | 0.608 / 0.395 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `decay-1y/k24/e2/lr0.1/l20.0001` | 0.478 / 0.316 (n=12,265) | 0.607 / 0.401 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `2y/k24/e2/lr0.1/l20.01` | 0.477 / 0.310 (n=12,265) | 0.606 / 0.393 (n=9,651) | 0.000 / 0.000 (n=2,614) | 26,369 / 57,183 |
| `decay-1y/k24/e1/lr0.1/l20.01` | 0.474 / 0.311 (n=12,265) | 0.602 / 0.395 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `full/k24/e2/lr0.1/l20.01` | 0.468 / 0.310 (n=12,265) | 0.595 / 0.394 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |
| `decay-1y/k24/e2/lr0.1/l20.01` | 0.465 / 0.311 (n=12,265) | 0.591 / 0.395 (n=9,651) | 0.000 / 0.000 (n=2,614) | 57,183 / 57,183 |

## Fold-2 verification

The parameter set below is the fold-1 frequent-label leader. No fold-2 retuning was performed.

| Candidate | Overall | Frequent | Rare |
|---|---:|---:|---:|
| Current Home SQL | 0.516 / 0.354 (n=10,534) | 0.557 / 0.394 (n=8,989) | 0.282 / 0.123 (n=1,545) |
| SQL, frequent candidates only | 0.490 / 0.344 (n=10,534) | 0.574 / 0.403 (n=8,989) | 0.000 / 0.000 (n=1,545) |
| Fold-1 leader `1y/k6/e1/lr0.03/l20.0001` | 0.490 / 0.378 (n=10,534) | 0.574 / 0.443 (n=8,989) | 0.000 / 0.000 (n=1,545) |
| Fold-1 eligible choice `1y/k12/e1/lr0.03/l20.0001` | 0.521 / 0.383 (n=10,534) | 0.610 / 0.449 (n=8,989) | 0.000 / 0.000 (n=1,545) |

## Decision

**Offline candidate `1y/k12/e1/lr0.03/l20.0001` passed the frequent-label gate on both folds, and the Kotlin implementation passed chronological parity.** Home is unchanged until Task 5 integrates the verified model; classic remains the default.

On fold 1, frequent HitRate@6 / MRR was **0.643 / 0.420** versus the
frequent-only SQL baseline **0.647 / 0.437** (deltas **-0.004 / -0.017**). On
fold 2 it was **0.610 / 0.449** versus **0.574 / 0.403** (deltas **+0.036 / +0.046**). The selected `k12` can promote frequent apps from just below SQL's top six, unlike the fold-1 leader `k6`. Rare-label metrics are zero by design because low-support candidates are omitted; users can still launch those apps from the full application list.

The Kotlin feature encoder, history-replay factory, and ranker are implemented
under `app/src/main/java/ovh/litapp/neurhome3/data/ml/`; synthetic JVM tests
pass. The chronological results above are from the Python offline prototype.
The Kotlin implementation passed chronological parity on 2026-09-30 via
`PairwiseHomeAppChronologicalParityTest` and an ephemeral anonymized fixture:
fold 1 **0.643 / 0.420** (identical) and fold 2 **0.608 / 0.448** versus
**0.610 / 0.449**, within the 0.003 tolerance and the 0.020 guard on both
folds. See the [Task 3 handoff](task-03-handoff.md) for the fixture format and
replay-divergence diagnostics.

## Reproduction

The local Room database is opened read-only. The output report is aggregate-only.

```sh
python3 -m pip install -r tools/requirements-home-selection-eval.txt
python3 tools/evaluate_home_app_reranker.py \
  --database /local/path/to/neurhome_database_sample.db \
  --output /tmp/home-app-reranker-report.md

# Kotlin chronological parity (optional; writes an anonymized, identifier-free
# fixture outside the repository and runs the fixture-gated JVM test):
python3 tools/evaluate_home_app_reranker.py \
  --database /local/path/to/neurhome_database_sample.db \
  --kotlin-parity-fixture /tmp/home-app-kotlin-parity.tsv
HOME_APP_RERANKER_PARITY_FIXTURE=/tmp/home-app-kotlin-parity.tsv \
  ./gradlew :app:testDevDebugUnitTest \
  --tests ovh.litapp.neurhome3.data.ml.PairwiseHomeAppChronologicalParityTest
```
