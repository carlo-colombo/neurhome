# Home app selection: local chronological evaluation

This report contains aggregate counts and metrics only. Package names, profile IDs, Wi-Fi values, geohashes, and coordinates are never emitted.

## History audit

- Valid launch rows: **106,084**; date range: **2020-04-08 through 2026-09-30**.
- App/profile labels: **348** across **2** profiles.
- Launch counts per profile (min / median / max): **2,195 / 53,042 / 103,889**.
- App/profile frequency (label count; corresponding launch count): 1: 56 labels / 56 launches; 2–9: 110 labels / 453 launches; 10–99: 105 labels / 3,613 launches; 100+: 77 labels / 101,962 launches.
- Wi-Fi: **64,153 (60.5%)** rows have a non-null value; **41,931 (39.5%)** are unknown. Null is not interpreted as no Wi-Fi.
- Location: coordinates on **106,005 (99.9%)** rows; coarse geohash available on **85,402 (80.5%)** rows and **994** distinct five-character cells.
- Distinct non-null Wi-Fi contexts: **153** (values are not reported).

## Evaluation protocol

- Rows are ordered chronologically (timestamp, then database row ID when present); hidden-from-Top packages are excluded from labels and scoring, matching Home filtering.
- Two expanding walk-forward folds: first train on the earliest 60% and test on 60–80%; then train on the earliest 80% and test on 80–100%. No row is randomly split across train/test.
- Each learned model is fitted once at its fold cutoff and held fixed for that test block; the next fold refits using its larger earlier-history prefix. Test examples never train their own prediction model.
- Frequent labels have at least 10 training launches; rare labels have fewer than 10, including labels not yet observed in training.
- Metrics are event-weighted HitRate@6 and MRR of the next launch. An unranked target contributes zero to both.
- Classic reproduces the current Home SQL score using only prior launches in its rolling four-calendar-month window, exact weekday/workday/weekend ratios, and the ±19-minute bins implied by the SQL's `< 20` minute condition. It does not use Wi-Fi or location.
- Learned features: profile, weekday, half-hour, cyclic clock/year, coarse five-character geohash, and a non-null Wi-Fi category. Missing Wi-Fi/location features are omitted. Linear training epochs: 4; neural epochs: 6. NumPy is the offline training/evaluation runtime.
- Horizons: all prior history; rolling 2 years; rolling 1 year; and all history with a one-year exponential half-life.

| Fold | Prior training rows | Train through | Test interval | Scored test launches |
|---|---:|---|---|---:|
| fold-1 | 62,188 | 2023-12-24 | 2023-12-24 – 2025-07-17 | 12,265 |
| fold-2 | 74,453 | 2025-07-17 | 2025-07-17 – 2026-09-30 | 10,534 |

## Aggregate results

Each metric cell is **HitRate@6 / MRR (events)**. Aggregate values combine both test folds.

| Candidate | All labels | Frequent labels | Rare labels |
|---|---:|---:|---:|
| Current Home SQL | 0.538 / 0.361 (n=22,799) | 0.584 / 0.405 (n=18,640) | 0.329 / 0.165 (n=4,159) |
| linear/full | 0.439 / 0.235 (n=22,799) | 0.537 / 0.287 (n=18,640) | 0.000 / 0.002 (n=4,159) |
| linear/2y | 0.452 / 0.265 (n=22,799) | 0.553 / 0.324 (n=18,640) | 0.001 / 0.002 (n=4,159) |
| linear/1y | 0.451 / 0.282 (n=22,799) | 0.552 / 0.344 (n=18,640) | 0.001 / 0.002 (n=4,159) |
| linear/decay-1y | 0.442 / 0.268 (n=22,799) | 0.540 / 0.327 (n=18,640) | 0.002 / 0.002 (n=4,159) |
| neural/full | 0.426 / 0.221 (n=22,799) | 0.520 / 0.270 (n=18,640) | 0.004 / 0.003 (n=4,159) |
| neural/2y | 0.446 / 0.267 (n=22,799) | 0.546 / 0.326 (n=18,640) | 0.001 / 0.002 (n=4,159) |
| neural/1y | 0.439 / 0.284 (n=22,799) | 0.537 / 0.347 (n=18,640) | 0.000 / 0.002 (n=4,159) |
| neural/decay-1y | 0.448 / 0.258 (n=22,799) | 0.548 / 0.315 (n=18,640) | 0.001 / 0.003 (n=4,159) |

## Fold-1 development results

This fold is used for candidate selection. A candidate must keep overall and rare-label HitRate@6 and MRR within 0.020 absolute of classic; among eligible candidates, choose highest overall HitRate@6, then MRR. Frequent-label results are shown but do not substitute for rare-label safety.

| Candidate | All labels | Frequent labels | Rare labels |
|---|---:|---:|---:|
| Current Home SQL | 0.556 / 0.367 (n=12,265) | 0.610 / 0.415 (n=9,651) | 0.357 / 0.190 (n=2,614) |
| linear/full | 0.401 / 0.182 (n=12,265) | 0.510 / 0.231 (n=9,651) | 0.000 / 0.001 (n=2,614) |
| linear/2y | 0.402 / 0.197 (n=12,265) | 0.511 / 0.250 (n=9,651) | 0.000 / 0.001 (n=2,614) |
| linear/1y | 0.396 / 0.199 (n=12,265) | 0.503 / 0.252 (n=9,651) | 0.000 / 0.002 (n=2,614) |
| linear/decay-1y | 0.397 / 0.198 (n=12,265) | 0.504 / 0.251 (n=9,651) | 0.000 / 0.001 (n=2,614) |
| neural/full | 0.382 / 0.185 (n=12,265) | 0.486 / 0.235 (n=9,651) | 0.000 / 0.001 (n=2,614) |
| neural/2y | 0.390 / 0.190 (n=12,265) | 0.496 / 0.241 (n=9,651) | 0.000 / 0.001 (n=2,614) |
| neural/1y | 0.366 / 0.198 (n=12,265) | 0.465 / 0.251 (n=9,651) | 0.000 / 0.002 (n=2,614) |
| neural/decay-1y | 0.404 / 0.199 (n=12,265) | 0.514 / 0.252 (n=9,651) | 0.000 / 0.001 (n=2,614) |

## Fold-2 later-period verification

Fold 2 is a later-period verification only, not used to choose the candidate. Acceptance requires the same overall and rare-label non-regression checks used in fold 1.

| Candidate | Overall HitRate@6 / MRR | Rare HitRate@6 / MRR |
|---|---:|---:|
| Current Home SQL | 0.516 / 0.354 (n=10,534) | 0.282 / 0.123 (n=1,545) |
| linear/full | 0.484 / 0.298 (n=10,534) | 0.001 / 0.002 (n=1,545) |
| linear/2y | 0.510 / 0.344 (n=10,534) | 0.003 / 0.003 (n=1,545) |
| linear/1y | 0.516 / 0.378 (n=10,534) | 0.002 / 0.002 (n=1,545) |
| linear/decay-1y | 0.495 / 0.350 (n=10,534) | 0.006 / 0.004 (n=1,545) |
| neural/full | 0.476 / 0.263 (n=10,534) | 0.010 / 0.005 (n=1,545) |
| neural/2y | 0.512 / 0.356 (n=10,534) | 0.003 / 0.004 (n=1,545) |
| neural/1y | 0.524 / 0.385 (n=10,534) | 0.000 / 0.003 (n=1,545) |
| neural/decay-1y | 0.500 / 0.327 (n=10,534) | 0.004 / 0.005 (n=1,545) |

## Decision

**Decision: no learned candidate passed the fold-1 development guard on both overall and rare labels. No learned model/runtime/horizon is selected.**

Fold 2 is shown as descriptive later-period evidence, not used to choose a replacement. Keep the Room/SQLite classic ranking as the production strategy (four-calendar-month history); revisit model selection after evaluating an explicit classic fallback for labels with fewer than 10 prior launches.

## Reproduction

From a clean checkout, install the single offline-evaluation dependency and provide a local Room database export (the personal export is not a build input):

```sh
python3 -m pip install -r tools/requirements-home-selection-eval.txt
python3 tools/evaluate_home_app_selection.py \
  --database /local/path/to/neurhome_database_sample.db \
  --output /tmp/home-app-selection-report.md
```

The database is opened read-only. The report contains no app/profile identifiers, SSIDs, geohashes, or coordinates.
