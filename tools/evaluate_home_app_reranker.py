#!/usr/bin/env python3
"""Tune and chronologically evaluate a local pairwise Home ranking residual.

The script reads the Room database read-only and writes aggregate results only.
It trains no Android checkpoint and never emits app/profile/context values.
"""

from __future__ import annotations

import argparse
import calendar
import itertools
import math
import sqlite3
import sys
from collections import Counter, deque
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path
from typing import Sequence

import numpy as np

import evaluate_home_app_selection as classic


HORIZONS = ("full", "2y", "1y", "decay-1y")
DEFAULT_CANDIDATE_COUNTS = (6, 12, 24)
DEFAULT_EPOCHS = (1, 2)
DEFAULT_LEARNING_RATES = (0.03, 0.1)
DEFAULT_L2_VALUES = (0.0001, 0.01)
MINIMUM_SUPPORT = 10
MAX_TUNED_CANDIDATES = max(DEFAULT_CANDIDATE_COUNTS)
REPORT_TITLE = "Home app selection: pairwise residual reranker experiment"


@dataclass(frozen=True)
class PairwiseExample:
    launch_index: int
    target_id: int
    # SQL order, filtered to labels with 10+ launches at this historical point.
    candidates: tuple[int, ...]


@dataclass(frozen=True)
class RerankerConfig:
    horizon: str
    candidate_count: int
    epochs: int
    learning_rate: float
    l2: float

    @property
    def key(self) -> str:
        return (
            f"{self.horizon}/k{self.candidate_count}/e{self.epochs}/"
            f"lr{self.learning_rate:g}/l2{self.l2:g}"
        )


KOTLIN_PARITY_CONFIG = RerankerConfig("1y", 12, 1, 0.03, 0.0001)


@dataclass(frozen=True)
class PairwiseModel:
    categorical: np.ndarray
    numeric: np.ndarray
    bias: np.ndarray
    classic_rank_weight: float


@dataclass
class RerankerEvaluation:
    fold_metrics: dict[str, dict[str, dict[str, classic.Metric]]]
    tuned_configs: list[RerankerConfig]
    fold1_leader: RerankerConfig
    selected_config: RerankerConfig | None
    fold_ranges: list[dict[str, object]]
    training_summaries: dict[str, dict[str, int]]


def _java_compatible_feature_hash(token: str) -> int:
    """Match Kotlin/JVM String.hashCode for portable local inference."""
    encoded = token.encode("utf-16-be", errors="surrogatepass")
    value = 0
    for offset in range(0, len(encoded), 2):
        unit = (encoded[offset] << 8) | encoded[offset + 1]
        value = (31 * value + unit) & 0xFFFFFFFF
    return (value & 0x7FFFFFFF) % classic.HASH_BINS + 1


def _prepare_reranker_features(
    launches: Sequence[classic.Launch],
) -> tuple[np.ndarray, np.ndarray]:
    """Build category IDs that the pure-Kotlin implementation can reproduce."""
    categories = np.zeros(
        (len(launches), classic.CAT_FEATURES), dtype=np.int32
    )
    numeric = np.zeros((len(launches), classic.NUM_FEATURES), dtype=np.float32)
    for index, launch in enumerate(launches):
        stamp = launch.timestamp
        minute = stamp.hour * 60 + stamp.minute
        tokens = [
            f"profile:{launch.profile}",
            f"weekday:{stamp.weekday()}",
            f"half-hour:{minute // 30}",
        ]
        if launch.geohash:
            tokens.append(f"geohash5:{launch.geohash[:5]}")
        if launch.wifi_state == "NO_WIFI":
            tokens.append("wifi:NO_WIFI")
        elif launch.wifi_state == "CONNECTED" and launch.wifi is not None:
            tokens.append(f"wifi:{launch.wifi}")
        category_ids = [_java_compatible_feature_hash(token) for token in tokens]
        categories[index, : len(category_ids)] = category_ids

        clock_phase = 2.0 * math.pi * minute / 1440.0
        year_length = 366 if calendar.isleap(stamp.year) else 365
        year_phase = 2.0 * math.pi * (stamp.timetuple().tm_yday - 1) / year_length
        numeric[index] = (
            math.sin(clock_phase),
            math.cos(clock_phase),
            math.sin(year_phase),
            math.cos(year_phase),
        )
    return categories, numeric


def _java_epoch_orders(sample_count: int, epochs: int, seed: int):
    """Yield the same Fisher-Yates orders as Collections.shuffle(Random(seed))."""
    mask = (1 << 48) - 1
    state = (seed ^ 0x5DEECE66D) & mask

    def next_bits(bit_count: int) -> int:
        nonlocal state
        state = (state * 0x5DEECE66D + 0xB) & mask
        return state >> (48 - bit_count)

    def next_int(bound: int) -> int:
        if bound & (bound - 1) == 0:
            return (bound * next_bits(31)) >> 31
        while True:
            bits = next_bits(31)
            value = bits % bound
            if bits - value + (bound - 1) < (1 << 31):
                return value

    for _ in range(epochs):
        order = list(range(sample_count))
        for index in range(sample_count, 1, -1):
            other = next_int(index)
            order[index - 1], order[other] = order[other], order[index - 1]
        yield order


def _build_training_examples(
    launches: Sequence[classic.Launch],
    hidden_packages: set[str],
    train_end: int,
    max_candidates: int = MAX_TUNED_CANDIDATES,
) -> list[PairwiseExample]:
    """Replay classic SQL history and make leakage-free pairwise examples."""
    labels = sorted({row.label for row in launches})
    label_ids = {label: index for index, label in enumerate(labels)}
    label_count = len(labels)
    visible = np.fromiter(
        (label[0] not in hidden_packages for label in labels),
        dtype=bool,
        count=label_count,
    )
    totals = np.zeros(label_count, dtype=np.int64)
    weekdays = np.zeros((label_count, 7), dtype=np.int64)
    day_types = np.zeros((label_count, 2), dtype=np.int64)
    minutes = np.zeros((label_count, 1440), dtype=np.int32)
    history: deque[tuple[datetime, int, int, int]] = deque()
    prior_counts: Counter[tuple[str, int]] = Counter()
    examples: list[PairwiseExample] = []

    for index, row in enumerate(launches[:train_end]):
        while history and history[0][0] <= classic._subtract_four_months(row.timestamp):
            _, expired_label, expired_minute, expired_weekday = history.popleft()
            totals[expired_label] -= 1
            weekdays[expired_label, expired_weekday] -= 1
            day_types[expired_label, 1 if expired_weekday in (5, 6) else 0] -= 1
            minutes[expired_label, expired_minute] -= 1

        target_id = label_ids[row.label]
        if row.package not in hidden_packages and prior_counts[row.label] >= MINIMUM_SUPPORT:
            minute = row.timestamp.hour * 60 + row.timestamp.minute
            weekday = row.timestamp.weekday()
            day_type = 1 if weekday in (5, 6) else 0
            window_indices = [(minute + offset) % 1440 for offset in range(-19, 20)]
            window_counts = minutes[:, window_indices].sum(axis=1)
            scores = window_counts * (
                weekdays[:, weekday] / np.maximum(totals, 1)
                + day_types[:, day_type] / np.maximum(totals, 1)
            )
            scores[~visible] = 0.0
            sql_order = np.flatnonzero(scores > 0.0)
            sql_order = sql_order[np.argsort(-scores[sql_order], kind="stable")]
            frequent_order = [
                int(candidate_id)
                for candidate_id in sql_order
                if prior_counts[labels[int(candidate_id)]] >= MINIMUM_SUPPORT
            ][:max_candidates]
            if target_id in frequent_order and len(frequent_order) > 1:
                examples.append(
                    PairwiseExample(index, target_id, tuple(frequent_order))
                )

        weekday = row.timestamp.weekday()
        minute = row.timestamp.hour * 60 + row.timestamp.minute
        label_id = target_id
        totals[label_id] += 1
        weekdays[label_id, weekday] += 1
        day_types[label_id, 1 if weekday in (5, 6) else 0] += 1
        minutes[label_id, minute] += 1
        history.append((row.timestamp, label_id, minute, weekday))
        if row.package not in hidden_packages:
            prior_counts[row.label] += 1

    return examples


def _horizon_examples(
    examples: Sequence[PairwiseExample],
    launches: Sequence[classic.Launch],
    train_end: int,
    horizon: str,
) -> tuple[list[PairwiseExample], np.ndarray]:
    if not examples:
        return [], np.empty(0, dtype=np.float32)
    if horizon == "full":
        selected = list(examples)
        return selected, np.ones(len(selected), dtype=np.float32)

    reference = launches[train_end - 1].timestamp
    if horizon == "decay-1y":
        selected = list(examples)
        weights = np.fromiter(
            (
                2.0 ** (
                    -(reference - launches[example.launch_index].timestamp).total_seconds()
                    / (365.0 * 86400.0)
                )
                for example in selected
            ),
            dtype=np.float32,
            count=len(selected),
        )
        weights /= max(float(weights.mean()), 1e-8)
        return selected, weights

    cutoff = reference - timedelta(days={"1y": 365, "2y": 730}[horizon])
    selected = [
        example
        for example in examples
        if launches[example.launch_index].timestamp >= cutoff
    ]
    return selected, np.ones(len(selected), dtype=np.float32)


def _fit_pairwise(
    examples: Sequence[PairwiseExample],
    sample_weights: np.ndarray,
    categories: np.ndarray,
    numeric: np.ndarray,
    label_count: int,
    config: RerankerConfig,
    seed: int = 1701,
) -> PairwiseModel:
    """Train a deterministic sparse pairwise logistic residual ranker."""
    categorical_weights = np.zeros(
        (label_count, classic.FEATURE_COUNT), dtype=np.float32
    )
    numeric_weights = np.zeros(
        (label_count, classic.NUM_FEATURES), dtype=np.float32
    )
    bias = np.zeros(label_count, dtype=np.float32)
    rank_weight = 0.0
    if not examples:
        return PairwiseModel(categorical_weights, numeric_weights, bias, rank_weight)

    for order in _java_epoch_orders(len(examples), config.epochs, seed):
        for sample_index in order:
            example = examples[sample_index]
            candidates = np.asarray(
                example.candidates[: config.candidate_count], dtype=np.int32
            )
            target_positions = np.flatnonzero(candidates == example.target_id)
            if len(target_positions) != 1 or len(candidates) < 2:
                continue
            target_position = int(target_positions[0])
            rival_positions = np.delete(
                np.arange(len(candidates), dtype=np.int32), target_position
            )
            rival_ids = candidates[rival_positions]

            active_categories = np.unique(
                categories[example.launch_index][
                    categories[example.launch_index] != 0
                ]
            )
            numeric_features = numeric[example.launch_index]
            rank_features = 1.0 / (np.arange(len(candidates), dtype=np.float32) + 1.0)

            candidate_scores = bias[candidates].copy()
            candidate_scores += categorical_weights[
                candidates[:, None], active_categories[None, :]
            ].sum(axis=1)
            candidate_scores += numeric_weights[candidates] @ numeric_features
            candidate_scores += rank_weight * rank_features
            differences = candidate_scores[target_position] - candidate_scores[
                rival_positions
            ]
            probabilities = 1.0 / (1.0 + np.exp(np.clip(differences, -30.0, 30.0)))
            weights = (
                probabilities
                * float(sample_weights[sample_index])
                / len(rival_ids)
            )
            learning_rate = config.learning_rate
            regularization = config.l2

            if len(active_categories):
                target_values = categorical_weights[
                    example.target_id, active_categories
                ]
                categorical_weights[example.target_id, active_categories] += (
                    learning_rate
                    * (weights.sum() - regularization * target_values)
                )
                rival_values = categorical_weights[
                    rival_ids[:, None], active_categories[None, :]
                ]
                categorical_weights[rival_ids[:, None], active_categories[None, :]] += (
                    learning_rate
                    * (-weights[:, None] - regularization * rival_values)
                )

            target_numeric = numeric_weights[example.target_id]
            numeric_weights[example.target_id] += learning_rate * (
                weights.sum() * numeric_features - regularization * target_numeric
            )
            rival_numeric = numeric_weights[rival_ids]
            numeric_weights[rival_ids] += learning_rate * (
                -weights[:, None] * numeric_features[None, :]
                - regularization * rival_numeric
            )

            bias[example.target_id] += learning_rate * (
                weights.sum() - regularization * bias[example.target_id]
            )
            bias[rival_ids] += learning_rate * (
                -weights - regularization * bias[rival_ids]
            )
            rank_difference = (
                rank_features[target_position] - rank_features[rival_positions]
            )
            rank_weight += learning_rate * (
                np.dot(weights, rank_difference) - regularization * rank_weight
            )

    return PairwiseModel(categorical_weights, numeric_weights, bias, rank_weight)


def _rerank_frequent_candidates(
    classic_ranking: Sequence[int],
    training_counts: Counter[tuple[str, int]],
    labels: Sequence[tuple[str, int]],
    categories: np.ndarray,
    numeric: np.ndarray,
    context_index: int,
    model: PairwiseModel,
    candidate_count: int,
) -> tuple[int, ...]:
    """Rerank only the classic candidate set and omit labels below support 10."""
    frequent = [
        label_id
        for label_id in classic_ranking
        if training_counts[labels[label_id]] >= MINIMUM_SUPPORT
    ]
    selected = frequent[:candidate_count]
    remaining = frequent[candidate_count:]
    if len(selected) < 2:
        return tuple(frequent)

    active_categories = np.unique(
        categories[context_index][categories[context_index] != 0]
    )
    candidate_ids = np.asarray(selected, dtype=np.int32)
    scores = model.bias[candidate_ids].copy()
    if len(active_categories):
        scores += model.categorical[candidate_ids[:, None], active_categories].sum(
            axis=1
        )
    scores += model.numeric[candidate_ids] @ numeric[context_index]
    scores += model.classic_rank_weight / (
        np.arange(len(candidate_ids), dtype=np.float32) + 1.0
    )
    order = np.argsort(-scores, kind="stable")
    return tuple(candidate_ids[order].tolist() + remaining)


def _rank_in(ranking: Sequence[int], target_id: int) -> int | None:
    try:
        return ranking.index(target_id) + 1
    except ValueError:
        return None


def _new_metrics() -> dict[str, classic.Metric]:
    return {bucket: classic.Metric() for bucket in ("all", "frequent", "rare")}


def _evaluate_rankings(
    launches: Sequence[classic.Launch],
    hidden_packages: set[str],
    test_start: int,
    test_end: int,
    classic_rankings: dict[int, tuple[int, ...]],
    training_counts: Counter[tuple[str, int]],
    labels: Sequence[tuple[str, int]],
    categories: np.ndarray,
    numeric: np.ndarray,
    config: RerankerConfig | None,
    model: PairwiseModel | None,
    include_baselines: bool = True,
) -> dict[str, dict[str, classic.Metric]]:
    metrics: dict[str, dict[str, classic.Metric]] = {}
    if include_baselines:
        metrics = {
            "classic": _new_metrics(),
            "classic-frequent-only": _new_metrics(),
        }
    if config is not None:
        metrics["reranker"] = _new_metrics()
    label_ids = {label: label_id for label_id, label in enumerate(labels)}

    for index in range(test_start, test_end):
        row = launches[index]
        if row.package in hidden_packages:
            continue
        target_id = label_ids[row.label]
        sql_ranking = classic_rankings[index]
        if include_baselines:
            frequent_sql = tuple(
                label_id
                for label_id in sql_ranking
                if training_counts[labels[label_id]] >= MINIMUM_SUPPORT
            )
            classic._add_rank(
                metrics["classic"],
                row.label,
                training_counts,
                _rank_in(sql_ranking, target_id),
            )
            classic._add_rank(
                metrics["classic-frequent-only"],
                row.label,
                training_counts,
                _rank_in(frequent_sql, target_id),
            )
        if config is not None and model is not None:
            reranked = _rerank_frequent_candidates(
                sql_ranking,
                training_counts,
                labels,
                categories,
                numeric,
                index,
                model,
                config.candidate_count,
            )
            classic._add_rank(
                metrics["reranker"],
                row.label,
                training_counts,
                _rank_in(reranked, target_id),
            )
    return metrics


def _within_guard(candidate: classic.Metric, baseline: classic.Metric) -> bool:
    return (
        candidate.hit_rate >= baseline.hit_rate - 0.020
        and candidate.mrr >= baseline.mrr - 0.020
    )


def _configuration_grid(
    horizons: Sequence[str] = HORIZONS,
    candidate_counts: Sequence[int] = DEFAULT_CANDIDATE_COUNTS,
    epochs_values: Sequence[int] = DEFAULT_EPOCHS,
    learning_rates: Sequence[float] = DEFAULT_LEARNING_RATES,
    l2_values: Sequence[float] = DEFAULT_L2_VALUES,
) -> list[RerankerConfig]:
    return [
        RerankerConfig(horizon, candidate_count, epochs, learning_rate, l2)
        for horizon, candidate_count, epochs, learning_rate, l2 in itertools.product(
            horizons, candidate_counts, epochs_values, learning_rates, l2_values
        )
    ]


def _fit_for_fold(
    launches: Sequence[classic.Launch],
    examples: Sequence[PairwiseExample],
    categories: np.ndarray,
    numeric: np.ndarray,
    train_end: int,
    label_count: int,
    config: RerankerConfig,
    seed: int,
) -> tuple[PairwiseModel, int]:
    selected_examples, weights = _horizon_examples(
        examples, launches, train_end, config.horizon
    )
    model = _fit_pairwise(
        selected_examples,
        weights,
        categories,
        numeric,
        label_count,
        config,
        seed,
    )
    return model, len(selected_examples)


def evaluate_reranker(
    launches: Sequence[classic.Launch],
    hidden_packages: set[str],
    candidate_counts: Sequence[int] = DEFAULT_CANDIDATE_COUNTS,
    epochs_values: Sequence[int] = DEFAULT_EPOCHS,
    learning_rates: Sequence[float] = DEFAULT_LEARNING_RATES,
    l2_values: Sequence[float] = DEFAULT_L2_VALUES,
    horizons: Sequence[str] = HORIZONS,
    seed: int = 1701,
    progress: bool = True,
) -> RerankerEvaluation:
    row_count = len(launches)
    fold1_end = int(row_count * 0.60)
    fold2_end = int(row_count * 0.80)
    folds = ((fold1_end, fold2_end), (fold2_end, row_count))
    if fold1_end < 2 or fold2_end <= fold1_end or row_count <= fold2_end:
        raise ValueError("At least five chronological launch rows are required")
    labels = sorted({row.label for row in launches})
    categories, numeric = _prepare_reranker_features(launches)
    baseline_metrics, baseline_rankings = classic._evaluate_baseline(
        launches, hidden_packages, folds
    )
    ranking_keys = list(baseline_rankings)

    configs = _configuration_grid(
        horizons, candidate_counts, epochs_values, learning_rates, l2_values
    )
    pairwise_examples = _build_training_examples(
        launches,
        hidden_packages,
        fold2_end,
        max(config.candidate_count for config in configs),
    )
    all_metrics: dict[str, dict[str, dict[str, classic.Metric]]] = {}
    summaries: dict[str, dict[str, int]] = {}
    fold_ranges: list[dict[str, object]] = []
    chosen_config: RerankerConfig | None = None
    fold1_leader: RerankerConfig | None = None
    for fold_number, (test_start, test_end) in enumerate(folds, start=1):
        ranking_key = ranking_keys[fold_number - 1]
        training_counts: Counter[tuple[str, int]] = Counter(
            row.label
            for row in launches[:test_start]
            if row.package not in hidden_packages
        )
        baseline_key = list(baseline_metrics)[fold_number - 1]
        baseline = baseline_metrics[baseline_key]
        fold_metrics: dict[str, dict[str, classic.Metric]] = {
            "classic": baseline,
        }
        filtered_metrics = _evaluate_rankings(
            launches,
            hidden_packages,
            test_start,
            test_end,
            baseline_rankings[ranking_key],
            training_counts,
            labels,
            categories,
            numeric,
            None,
            None,
        )
        fold_metrics["classic-frequent-only"] = filtered_metrics[
            "classic-frequent-only"
        ]
        fold_ranges.append(
            {
                "name": f"fold-{fold_number}",
                "train_rows": sum(
                    launches[index].package not in hidden_packages
                    for index in range(test_start)
                ),
                "training_end": launches[test_start - 1].timestamp.date(),
                "test_start": launches[test_start].timestamp.date(),
                "test_end": launches[test_end - 1].timestamp.date(),
                "test_rows": sum(
                    launches[index].package not in hidden_packages
                    for index in range(test_start, test_end)
                ),
            }
        )

        if fold_number == 1:
            fold_examples = [
                example
                for example in pairwise_examples
                if example.launch_index < test_start
            ]
            config_metrics: dict[str, dict[str, classic.Metric]] = {}
            for config_index, config in enumerate(configs, start=1):
                if progress:
                    print(
                        f"Tuning fold 1: {config_index}/{len(configs)} "
                        f"({config.key})...",
                        file=sys.stderr,
                    )
                model, used_examples = _fit_for_fold(
                    launches,
                    fold_examples,
                    categories,
                    numeric,
                    test_start,
                    len(labels),
                    config,
                    seed,
                )
                result = _evaluate_rankings(
                    launches,
                    hidden_packages,
                    test_start,
                    test_end,
                    baseline_rankings[ranking_key],
                    training_counts,
                    labels,
                    categories,
                    numeric,
                    config,
                    model,
                    include_baselines=False,
                )["reranker"]
                config_metrics[config.key] = result
                summaries[f"fold-1/{config.key}"] = {
                    "training_examples": used_examples,
                    "pairwise_events": len(fold_examples),
                }
            fold_metrics.update(config_metrics)
            baseline_frequent = fold_metrics["classic-frequent-only"]["frequent"]
            fold1_leader = max(
                configs,
                key=lambda config: (
                    config_metrics[config.key]["frequent"].hit_rate,
                    config_metrics[config.key]["frequent"].mrr,
                ),
            )
            eligible = [
                config
                for config in configs
                if _within_guard(
                    config_metrics[config.key]["frequent"], baseline_frequent
                )
            ]
            if eligible:
                chosen_config = max(
                    eligible,
                    key=lambda config: (
                        config_metrics[config.key]["frequent"].hit_rate,
                        config_metrics[config.key]["frequent"].mrr,
                        -config.candidate_count,
                        -config.epochs,
                        -config.learning_rate,
                        -config.l2,
                    ),
                )
        else:
            # Verify the eligible fold-1 choice; if none passed the development
            # guard, verify the fold-1 leader for diagnosis only.
            verify_configs = [chosen_config or fold1_leader]
            if fold1_leader is not None and fold1_leader not in verify_configs:
                verify_configs.append(fold1_leader)
            for verify_config in verify_configs:
                if verify_config is None:
                    continue
                fold_examples = [
                    example
                    for example in pairwise_examples
                    if example.launch_index < test_start
                ]
                model, used_examples = _fit_for_fold(
                    launches,
                    fold_examples,
                    categories,
                    numeric,
                    test_start,
                    len(labels),
                    verify_config,
                    seed,
                )
                fold_metrics[verify_config.key] = _evaluate_rankings(
                    launches,
                    hidden_packages,
                    test_start,
                    test_end,
                    baseline_rankings[ranking_key],
                    training_counts,
                    labels,
                    categories,
                    numeric,
                    verify_config,
                    model,
                    include_baselines=False,
                )["reranker"]
                summaries[f"fold-2/{verify_config.key}"] = {
                    "training_examples": used_examples,
                    "pairwise_events": len(fold_examples),
                }
        all_metrics[f"fold-{fold_number}"] = fold_metrics

    return RerankerEvaluation(
        all_metrics,
        configs,
        fold1_leader,
        chosen_config,
        fold_ranges,
        summaries,
    )


def export_kotlin_parity_fixture(
    launches: Sequence[classic.Launch],
    hidden_packages: set[str],
    output: Path,
    config: RerankerConfig = KOTLIN_PARITY_CONFIG,
) -> None:
    """Write a temporary, identifier-free corpus for the Kotlin JVM parity test.

    Package/profile labels become integer IDs and context is exported only as
    the same hashed feature IDs/numeric vector consumed by the Kotlin model.
    No package names, SSIDs, coordinates, or geohashes are written.
    """
    row_count = len(launches)
    fold1_start = int(row_count * 0.60)
    fold2_start = int(row_count * 0.80)
    folds = ((fold1_start, fold2_start), (fold2_start, row_count))
    if fold1_start < 2 or fold2_start <= fold1_start or row_count <= fold2_start:
        raise ValueError("At least five chronological launch rows are required")

    labels = sorted({row.label for row in launches})
    label_ids = {label: index for index, label in enumerate(labels)}
    hidden_ids = sorted(
        label_id
        for label_id, (package, _) in enumerate(labels)
        if package in hidden_packages
    )
    categories, numeric = _prepare_reranker_features(launches)
    _, baseline_rankings = classic._evaluate_baseline(launches, hidden_packages, folds)
    training_examples = _build_training_examples(
        launches, hidden_packages, fold2_start, MAX_TUNED_CANDIDATES
    )

    lines = [
        "\t".join(
            (
                "M",
                str(row_count),
                str(fold1_start),
                str(fold2_start),
                ",".join(map(str, hidden_ids)),
            )
        )
    ]
    for index, row in enumerate(launches):
        feature_ids = ",".join(map(str, categories[index]))
        numeric_features = ",".join(format(float(value), ".9g") for value in numeric[index])
        lines.append(
            "\t".join(
                (
                    "L",
                    str(index),
                    row.timestamp.isoformat(),
                    str(label_ids[row.label]),
                    str(row.profile),
                    feature_ids,
                    numeric_features,
                )
            )
        )
    examples_by_index = {example.launch_index: example for example in training_examples}
    prior_counts: Counter[tuple[str, int]] = Counter()
    for index, row in enumerate(launches):
        example = examples_by_index.get(index)
        if example is not None:
            candidates = ",".join(
                f"{candidate}:{prior_counts[labels[candidate]]}"
                for candidate in example.candidates
            )
            lines.append(
                "\t".join(
                    (
                        "P",
                        str(index),
                        str(example.target_id),
                        str(prior_counts[row.label]),
                        candidates,
                    )
                )
            )
        if row.package not in hidden_packages:
            prior_counts[row.label] += 1

    for fold_number, (test_start, test_end) in enumerate(folds, start=1):
        fold_examples = [
            example for example in training_examples
            if example.launch_index < test_start
        ]
        model, _ = _fit_for_fold(
            launches,
            fold_examples,
            categories,
            numeric,
            test_start,
            len(labels),
            config,
            seed=1701,
        )
        training_counts: Counter[tuple[str, int]] = Counter(
            row.label
            for row in launches[:test_start]
            if row.package not in hidden_packages
        )
        ranking_key = list(baseline_rankings)[fold_number - 1]
        rankings = baseline_rankings[ranking_key]
        for index in range(test_start, test_end):
            row = launches[index]
            if row.package in hidden_packages:
                continue
            classic_order = rankings[index]
            candidate_data = ",".join(
                f"{candidate}:{training_counts[labels[candidate]]}"
                for candidate in classic_order
            )
            predicted = _rerank_frequent_candidates(
                classic_order,
                training_counts,
                labels,
                categories,
                numeric,
                index,
                model,
                config.candidate_count,
            )
            expected_rank = _rank_in(predicted, label_ids[row.label]) or 0
            frequent_sql = tuple(
                candidate for candidate in classic_order
                if training_counts[labels[candidate]] >= MINIMUM_SUPPORT
            )
            baseline_rank = _rank_in(frequent_sql, label_ids[row.label]) or 0
            lines.append(
                "\t".join(
                    (
                        "E",
                        str(fold_number),
                        str(index),
                        str(label_ids[row.label]),
                        candidate_data,
                        str(expected_rank),
                        str(baseline_rank),
                    )
                )
            )

    output.write_text("\n".join(lines) + "\n", encoding="utf-8")


def _metric_text(metric: classic.Metric) -> str:
    return f"{metric.hit_rate:.3f} / {metric.mrr:.3f} (n={metric.count:,})"


def render_report(
    audit: dict[str, object],
    evaluation: RerankerEvaluation,
) -> str:
    fold1 = evaluation.fold_metrics["fold-1"]
    fold2 = evaluation.fold_metrics["fold-2"]
    leader = max(
        evaluation.tuned_configs,
        key=lambda config: (
            fold1[config.key]["frequent"].hit_rate,
            fold1[config.key]["frequent"].mrr,
        ),
    )
    classic_frequency = fold1["classic-frequent-only"]["frequent"]
    leader_frequency = fold1[leader.key]["frequent"]
    selected = evaluation.selected_config
    selected_late = fold2.get(selected.key) if selected else None
    gate_passed = bool(
        selected_late
        and _within_guard(
            selected_late["frequent"],
            fold2["classic-frequent-only"]["frequent"],
        )
    )

    lines = [
        f"# {REPORT_TITLE}",
        "",
        "Aggregate-only output: app/profile identifiers, SSIDs, geohashes, and coordinates are never emitted.",
        "",
        "## Product objective and evaluation gate",
        "",
        "Rare apps may be absent from the six Home recommendations because the full application list remains available. Therefore model selection is based on frequent-label HitRate@6 and MRR; rare-label and all-launch metrics are diagnostics, not release gates.",
        "The candidate is drawn only from positive-score classic SQL results, and only labels with at least 10 prior launches are eligible for the reranker. The baseline comparison also filters classic to those frequent candidates so rare-label slots do not consume recommendation positions in either arm.",
        "A candidate must be within 0.020 absolute of the frequent-only classic baseline on both frequent-label HitRate@6 and MRR on fold 1 and fold 2. Fold 1 selects/tunes; fold 2 verifies without retuning.",
        "",
        "## Method",
        "",
        f"- Launch history: **{audit['launches']:,}** rows, **{audit['app_profile_labels']:,}** app/profile labels, {audit['profiles']} profiles.",
        f"- Wi-Fi context: **{audit['wifi_known_rows']:,}** connected, **{audit['wifi_no_wifi_rows']:,}** known NO_WIFI, **{audit['wifi_unknown_rows']:,}** unknown. Unknown is omitted; legacy retained SSIDs count as connected evidence.",
        "- Two chronological expanding folds: earliest 60% trains fold 1 and 60–80% tests; earliest 80% trains fold 2 and 80–100% tests.",
        "- At each historical training event, classic SQL candidates are reconstructed using only earlier launches in the rolling four-calendar-month window. Pairwise examples include a target only when it is among the classic candidates and has at least 10 earlier launches; comparisons are only between labels with at least 10 prior launches.",
        "- The pairwise logistic ranker learns per-label context interactions, label bias, and a coefficient for the classic rank signal. Context is the existing deterministic profile/time/day/coarse-location/Wi-Fi encoding. The output can reorder only classic candidates; rare candidates are omitted from recommendations, not removed from the installed-app list.",
        "- Training is deterministic for a fixed seed/configuration. All tuning uses fold 1. There is no checkpoint, Android model, network access, or Home integration in this offline experiment.",
        "",
        "## Automatic parameter sweep",
        "",
        f"The deterministic grid searched **{len(evaluation.tuned_configs)}** configurations: horizons `{', '.join(dict.fromkeys(config.horizon for config in evaluation.tuned_configs))}`; frequent candidate limits `{', '.join(map(str, dict.fromkeys(config.candidate_count for config in evaluation.tuned_configs)))}`; epochs `{', '.join(map(str, dict.fromkeys(config.epochs for config in evaluation.tuned_configs)))}`; learning rates `{', '.join(map(str, dict.fromkeys(config.learning_rate for config in evaluation.tuned_configs)))}`; L2 values `{', '.join(map(str, dict.fromkeys(config.l2 for config in evaluation.tuned_configs)))}`. The fold-1 leader is chosen by frequent-label HitRate@6, then MRR; eligible candidates must meet the fold-1 0.020 guard. Fold 2 never tunes parameters.",
        "",
        "| Fold | Prior training rows | Train through | Test interval | Scored launches |",
        "|---|---:|---|---|---:|",
    ]
    for fold in evaluation.fold_ranges:
        lines.append(
            f"| {fold['name']} | {fold['train_rows']:,} | {fold['training_end']} | "
            f"{fold['test_start']} – {fold['test_end']} | {fold['test_rows']:,} |"
        )

    lines.extend(
        [
            "",
            "## Fold-1 tuning results",
            "",
            "Metrics are HitRate@6 / MRR. Overall and rare-label values are reported for visibility; only frequent-label metrics drive tuning/acceptance.",
            "",
            "| Candidate | Overall | Frequent | Rare | Pairwise training examples |",
            "|---|---:|---:|---:|---:|",
        ]
    )
    for name in ("classic", "classic-frequent-only"):
        metrics = fold1[name]
        display = "Current Home SQL" if name == "classic" else "SQL, frequent candidates only"
        lines.append(
            f"| {display} | {_metric_text(metrics['all'])} | "
            f"{_metric_text(metrics['frequent'])} | {_metric_text(metrics['rare'])} | — |"
        )
    ranked_configs = sorted(
        evaluation.tuned_configs,
        key=lambda config: (
            fold1[config.key]["frequent"].hit_rate,
            fold1[config.key]["frequent"].mrr,
        ),
        reverse=True,
    )
    for config in ranked_configs:
        metrics = fold1[config.key]
        summary = evaluation.training_summaries[f"fold-1/{config.key}"]
        lines.append(
            f"| `{config.key}` | {_metric_text(metrics['all'])} | "
            f"{_metric_text(metrics['frequent'])} | {_metric_text(metrics['rare'])} | "
            f"{summary['training_examples']:,} / {summary['pairwise_events']:,} |"
        )

    lines.extend(
        [
            "",
            "## Fold-2 verification",
            "",
            "The parameter set below is the fold-1 frequent-label leader. No fold-2 retuning was performed.",
            "",
            "| Candidate | Overall | Frequent | Rare |",
            "|---|---:|---:|---:|",
        ]
    )
    for name in ("classic", "classic-frequent-only"):
        metrics = fold2[name]
        display = "Current Home SQL" if name == "classic" else "SQL, frequent candidates only"
        lines.append(
            f"| {display} | {_metric_text(metrics['all'])} | "
            f"{_metric_text(metrics['frequent'])} | {_metric_text(metrics['rare'])} |"
        )
    verified_configs = [evaluation.fold1_leader]
    if (
        evaluation.selected_config is not None
        and evaluation.selected_config.key != evaluation.fold1_leader.key
    ):
        verified_configs.append(evaluation.selected_config)
    for config in verified_configs:
        late = fold2.get(config.key)
        if late is None:
            continue
        display = (
            "Fold-1 leader"
            if config.key == evaluation.fold1_leader.key
            else "Fold-1 eligible choice"
        )
        lines.append(
            f"| {display} `{config.key}` | "
            f"{_metric_text(late['all'])} | {_metric_text(late['frequent'])} | "
            f"{_metric_text(late['rare'])} |"
        )

    lines.extend(["", "## Decision", ""])
    if selected is None:
        lines.append(
            f"**No candidate passed the fold-1 frequent-label guard.** Best fold-1 frequent result was `{leader.key}` at {_metric_text(leader_frequency)} versus the frequent-only SQL baseline {_metric_text(classic_frequency)}."
        )
        lines.append(
            "The reranker is not selected. Keep the current SQL ranking in production; the app-list route remains available for every installed app."
        )
    elif not gate_passed:
        lines.append(
            f"**Fold-1 candidate `{selected.key}` failed fold-2 frequent-label verification.** It remains unselected; fold 2 is not used to retune."
        )
    else:
        lines.append(
            f"**Offline candidate `{selected.key}` passed the frequent-label gate on both folds.** This selects the parameter configuration for a separate on-device implementation review; it does not itself change Home or create a production checkpoint."
        )
    lines.extend(
        [
            "",
            "## Reproduction",
            "",
            "The local Room database is opened read-only. The output report is aggregate-only.",
            "",
            "```sh",
            "python3 -m pip install -r tools/requirements-home-selection-eval.txt",
            "python3 tools/evaluate_home_app_reranker.py \\",
            "  --database /local/path/to/neurhome_database_sample.db \\",
            "  --output /tmp/home-app-reranker-report.md",
            "```",
            "",
        ]
    )
    return "\n".join(lines)


def _parse_csv(value: str, cast, name: str):
    try:
        result = tuple(cast(part.strip()) for part in value.split(",") if part.strip())
    except ValueError:
        raise argparse.ArgumentTypeError(f"invalid {name} list") from None
    if not result:
        raise argparse.ArgumentTypeError(f"{name} list must not be empty")
    return result


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", required=True, type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument(
        "--kotlin-parity-fixture",
        type=Path,
        help="write a temporary anonymized corpus for the Kotlin JVM parity test and exit",
    )
    parser.add_argument("--seed", type=int, default=1701)
    parser.add_argument(
        "--horizons",
        type=lambda value: _parse_csv(value, str, "horizon"),
        default=HORIZONS,
    )
    parser.add_argument(
        "--candidate-counts",
        type=lambda value: _parse_csv(value, int, "candidate count"),
        default=DEFAULT_CANDIDATE_COUNTS,
    )
    parser.add_argument(
        "--epochs",
        type=lambda value: _parse_csv(value, int, "epoch"),
        default=DEFAULT_EPOCHS,
    )
    parser.add_argument(
        "--learning-rates",
        type=lambda value: _parse_csv(value, float, "learning rate"),
        default=DEFAULT_LEARNING_RATES,
    )
    parser.add_argument(
        "--l2-values",
        type=lambda value: _parse_csv(value, float, "L2"),
        default=DEFAULT_L2_VALUES,
    )
    args = parser.parse_args(argv)
    if any(horizon not in HORIZONS for horizon in args.horizons):
        parser.error(f"horizons must be selected from: {', '.join(HORIZONS)}")
    if any(value < 1 for value in args.candidate_counts + args.epochs):
        parser.error("candidate counts and epochs must be positive")
    if any(value <= 0 for value in args.learning_rates) or any(
        value < 0 for value in args.l2_values
    ):
        parser.error("learning rates must be positive and L2 values non-negative")
    try:
        launches, hidden_packages = classic.load_database(args.database)
        if args.kotlin_parity_fixture:
            export_kotlin_parity_fixture(
                launches, hidden_packages, args.kotlin_parity_fixture
            )
            print(f"Wrote anonymized Kotlin parity fixture: {args.kotlin_parity_fixture}")
            return 0
        audit = classic.audit_history(launches)
        evaluation = evaluate_reranker(
            launches,
            hidden_packages,
            candidate_counts=args.candidate_counts,
            epochs_values=args.epochs,
            learning_rates=args.learning_rates,
            l2_values=args.l2_values,
            horizons=args.horizons,
            seed=args.seed,
        )
        report = render_report(audit, evaluation).rstrip() + "\n"
        if args.output:
            args.output.write_text(report, encoding="utf-8")
            print(f"Wrote aggregate report: {args.output}")
        else:
            print(report, end="")
    except (OSError, ValueError, sqlite3.Error) as exc:
        print(f"Reranker evaluation failed: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
