#!/usr/bin/env python3
"""Privacy-preserving chronological backtest for Home app ranking.

Only aggregate audit/metric output is written. The database is opened read-only;
package names, profile IDs, Wi-Fi values, and location values stay in memory.
"""

from __future__ import annotations

import argparse
import calendar
import hashlib
import math
import sqlite3
import sys
from collections import Counter, defaultdict, deque
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path
from typing import Sequence

try:
    import numpy as np
except ImportError as exc:  # pragma: no cover - depends on local environment
    raise SystemExit(
        "NumPy is required. Install it with: "
        "python3 -m pip install -r tools/requirements-home-selection-eval.txt"
    ) from exc


HASH_BINS = 4096
FEATURE_COUNT = HASH_BINS + 1  # index zero is the fixed padding feature
CAT_FEATURES = 5
NUM_FEATURES = 4
HORIZONS = ("full", "2y", "1y", "decay-1y")
MODEL_NAMES = ("linear", "neural")
REPORT_TITLE = "Home app selection: local chronological evaluation"


@dataclass(frozen=True)
class Launch:
    package: str
    profile: int
    timestamp: datetime
    wifi: str | None
    latitude: float | None
    longitude: float | None
    geohash: str | None
    wifi_state: str = "UNKNOWN"

    @property
    def label(self) -> tuple[str, int]:
        return (self.package, self.profile)


@dataclass
class Metric:
    count: int = 0
    hits: int = 0
    reciprocal_rank_sum: float = 0.0

    def add(self, rank: int | None) -> None:
        self.count += 1
        if rank is not None:
            self.reciprocal_rank_sum += 1.0 / rank
            if rank <= 6:
                self.hits += 1

    @property
    def hit_rate(self) -> float:
        return self.hits / self.count if self.count else 0.0

    @property
    def mrr(self) -> float:
        return self.reciprocal_rank_sum / self.count if self.count else 0.0

    def merge(self, other: "Metric") -> None:
        self.count += other.count
        self.hits += other.hits
        self.reciprocal_rank_sum += other.reciprocal_rank_sum


@dataclass
class Evaluation:
    # fold -> model/horizon -> frequency bucket -> metric
    metrics: dict[str, dict[str, dict[str, Metric]]]
    train_counts: dict[str, dict[str, int]]
    fold_ranges: list[dict[str, object]]


def _parse_timestamp(value: str) -> datetime:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is not None:
        parsed = parsed.astimezone().replace(tzinfo=None)
    return parsed


def load_database(database_path: Path) -> tuple[list[Launch], set[str]]:
    uri = f"file:{database_path.resolve().as_posix()}?mode=ro"
    try:
        connection = sqlite3.connect(uri, uri=True)
    except sqlite3.Error as exc:
        raise ValueError("Unable to open the supplied database read-only") from exc
    try:
        tables = {
            row[0]
            for row in connection.execute(
                "SELECT name FROM sqlite_master WHERE type = 'table'"
            )
        }
        if "ApplicationLogEntry" not in tables:
            raise ValueError("Database does not contain ApplicationLogEntry")
        columns = {
            row[1]
            for row in connection.execute("PRAGMA table_info(ApplicationLogEntry)")
        }
        required = {"packageName", "user", "timestamp", "wifi"}
        if not required.issubset(columns):
            raise ValueError("ApplicationLogEntry is missing required columns")
        optional = ("latitude", "longitude", "geohash")
        wifi_state_column = (
            "wifiState" if "wifiState" in columns else "NULL AS wifiState"
        )
        selected = [
            "packageName",
            "user",
            "timestamp",
            "wifi",
            wifi_state_column,
            *(name if name in columns else f"NULL AS {name}" for name in optional),
        ]
        launches: list[Launch] = []
        for package, profile, stamp, wifi, wifi_state, latitude, longitude, geohash in connection.execute(
            f"SELECT {', '.join(selected)} FROM ApplicationLogEntry "
            "ORDER BY timestamp, uid"
            if "uid" in columns
            else f"SELECT {', '.join(selected)} FROM ApplicationLogEntry ORDER BY timestamp"
        ):
            try:
                timestamp = _parse_timestamp(str(stamp))
            except (TypeError, ValueError):
                continue
            try:
                ssid = None if wifi is None else str(wifi)
                state = str(wifi_state or "UNKNOWN")
                if state not in {"CONNECTED", "NO_WIFI", "UNKNOWN"}:
                    state = "UNKNOWN"
                # Before v20, a retained SSID is the only available evidence that
                # this launch had a connected Wi-Fi context.
                if state == "UNKNOWN" and ssid is not None:
                    state = "CONNECTED"
                launches.append(
                    Launch(
                        package=str(package),
                        profile=int(profile),
                        timestamp=timestamp,
                        wifi=ssid,
                        latitude=None if latitude is None else float(latitude),
                        longitude=None if longitude is None else float(longitude),
                        geohash=None if geohash is None else str(geohash),
                        wifi_state=state,
                    )
                )
            except (TypeError, ValueError):
                raise ValueError(
                    "Launch history contains malformed profile or coordinate data"
                ) from None

        hidden_packages: set[str] = set()
        if "AdditionalPackageMetadata" in tables:
            metadata_columns = {
                row[1]
                for row in connection.execute(
                    "PRAGMA table_info(AdditionalPackageMetadata)"
                )
            }
            if {"packageName", "hideFrom"}.issubset(metadata_columns):
                hidden_packages = {
                    row[0]
                    for row in connection.execute(
                        "SELECT packageName FROM AdditionalPackageMetadata "
                        "WHERE hideFrom = 'TOP'"
                    )
                }
        return launches, hidden_packages
    except sqlite3.Error as exc:
        raise ValueError("Unable to read the launch history schema") from exc
    finally:
        connection.close()


def _frequency_bin(count: int) -> str:
    if count == 1:
        return "1"
    if count < 10:
        return "2–9"
    if count < 100:
        return "10–99"
    return "100+"


def audit_history(launches: Sequence[Launch]) -> dict[str, object]:
    if not launches:
        raise ValueError("The supplied database contains no valid launch rows")
    profile_events = Counter(row.profile for row in launches)
    label_events = Counter(row.label for row in launches)
    label_event_bins: dict[str, int] = defaultdict(int)
    label_count_bins: dict[str, int] = defaultdict(int)
    for count in label_events.values():
        bucket = _frequency_bin(count)
        label_count_bins[bucket] += 1
        label_event_bins[bucket] += count
    sorted_profile_counts = sorted(profile_events.values())
    geohash_rows = sum(bool(row.geohash) for row in launches)
    coordinate_rows = sum(
        row.latitude is not None and row.longitude is not None for row in launches
    )
    wifi_known_rows = sum(row.wifi_state == "CONNECTED" for row in launches)
    wifi_no_wifi_rows = sum(row.wifi_state == "NO_WIFI" for row in launches)
    wifi_unknown_rows = sum(row.wifi_state == "UNKNOWN" for row in launches)
    wifi_contexts = {row.wifi for row in launches if row.wifi is not None}
    location_cells = {row.geohash[:5] for row in launches if row.geohash}
    return {
        "launches": len(launches),
        "start": launches[0].timestamp.date(),
        "end": launches[-1].timestamp.date(),
        "profiles": len(profile_events),
        "profile_launch_min": sorted_profile_counts[0],
        "profile_launch_median": _median(sorted_profile_counts),
        "profile_launch_max": sorted_profile_counts[-1],
        "app_profile_labels": len(label_events),
        "label_count_bins": dict(label_count_bins),
        "label_event_bins": dict(label_event_bins),
        "wifi_known_rows": wifi_known_rows,
        "wifi_no_wifi_rows": wifi_no_wifi_rows,
        "wifi_unknown_rows": wifi_unknown_rows,
        "distinct_wifi_contexts": len(wifi_contexts),
        "coordinate_rows": coordinate_rows,
        "geohash_rows": geohash_rows,
        "coarse_location_cells": len(location_cells),
    }


def _median(values: Sequence[int]) -> float:
    middle = len(values) // 2
    if len(values) % 2:
        return float(values[middle])
    return (values[middle - 1] + values[middle]) / 2.0


def _stable_hash(token: str) -> int:
    digest = hashlib.blake2s(token.encode("utf-8"), digest_size=4).digest()
    return int.from_bytes(digest, "little") % HASH_BINS + 1


def encode_context(launch: Launch) -> tuple[list[int], list[float]]:
    """Encode categorical context; absent Wi-Fi/location adds no feature."""
    stamp = launch.timestamp
    minute = stamp.hour * 60 + stamp.minute
    clock_phase = 2.0 * math.pi * minute / 1440.0
    year_length = 366 if calendar.isleap(stamp.year) else 365
    year_phase = 2.0 * math.pi * (stamp.timetuple().tm_yday - 1) / year_length
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
    categories = [_stable_hash(token) for token in tokens]
    categories.extend([0] * (CAT_FEATURES - len(categories)))
    numeric = [
        math.sin(clock_phase),
        math.cos(clock_phase),
        math.sin(year_phase),
        math.cos(year_phase),
    ]
    return categories, numeric


def _prepare_features(
    launches: Sequence[Launch],
) -> tuple[np.ndarray, np.ndarray]:
    categories = np.zeros((len(launches), CAT_FEATURES), dtype=np.int32)
    numeric = np.zeros((len(launches), NUM_FEATURES), dtype=np.float32)
    for i, launch in enumerate(launches):
        cat, num = encode_context(launch)
        categories[i] = cat
        numeric[i] = num
    return categories, numeric


def _horizon_mask(
    launches: Sequence[Launch], train_end: int, horizon: str
) -> np.ndarray:
    if horizon == "full":
        return np.ones(train_end, dtype=bool)
    if horizon == "decay-1y":
        return np.ones(train_end, dtype=bool)
    days = {"2y": 730, "1y": 365}[horizon]
    cutoff = launches[train_end - 1].timestamp - timedelta(days=days)
    return np.fromiter(
        (launches[i].timestamp >= cutoff for i in range(train_end)),
        dtype=bool,
        count=train_end,
    )


def _weighted_adam_update(
    parameter: np.ndarray,
    gradient: np.ndarray,
    first_moment: np.ndarray,
    second_moment: np.ndarray,
    step: int,
    learning_rate: float,
) -> None:
    beta1, beta2 = 0.9, 0.999
    first_moment *= beta1
    first_moment += (1.0 - beta1) * gradient
    second_moment *= beta2
    second_moment += (1.0 - beta2) * np.square(gradient)
    m_hat = first_moment / (1.0 - beta1**step)
    v_hat = second_moment / (1.0 - beta2**step)
    parameter -= learning_rate * m_hat / (np.sqrt(v_hat) + 1e-8)


def _sample_weights(
    launches: Sequence[Launch], indices: np.ndarray, horizon: str
) -> np.ndarray:
    if horizon != "decay-1y":
        return np.ones(len(indices), dtype=np.float32)
    reference = launches[int(indices[-1])].timestamp
    weights = np.fromiter(
        (
            2.0 ** (-(reference - launches[int(i)].timestamp).total_seconds() / (365.0 * 86400.0))
            for i in indices
        ),
        dtype=np.float32,
        count=len(indices),
    )
    weights /= max(float(weights.mean()), 1e-8)
    return weights


def _fit_linear(
    categories: np.ndarray,
    numeric: np.ndarray,
    targets: np.ndarray,
    weights: np.ndarray,
    class_count: int,
    epochs: int,
    seed: int,
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    rng = np.random.default_rng(seed)
    categorical_weights = np.zeros((FEATURE_COUNT, class_count), dtype=np.float32)
    numeric_weights = np.zeros((NUM_FEATURES, class_count), dtype=np.float32)
    bias = np.zeros(class_count, dtype=np.float32)
    parameters = [categorical_weights, numeric_weights, bias]
    first = [np.zeros_like(p) for p in parameters]
    second = [np.zeros_like(p) for p in parameters]
    step = 0
    batch_size = 512
    learning_rate = 0.035
    regularization = 2e-5
    sample_count = len(targets)

    for _ in range(epochs):
        order = rng.permutation(sample_count)
        for start in range(0, sample_count, batch_size):
            batch = order[start : start + batch_size]
            cats = categories[batch]
            nums = numeric[batch]
            batch_targets = targets[batch]
            batch_weights = weights[batch]
            normalization = max(float(batch_weights.sum()), 1e-8)
            logits = np.broadcast_to(bias, (len(batch), class_count)).copy()
            logits += nums @ numeric_weights
            for slot in range(CAT_FEATURES):
                logits += categorical_weights[cats[:, slot]]
            logits -= logits.max(axis=1, keepdims=True)
            probabilities = np.exp(logits)
            probabilities /= probabilities.sum(axis=1, keepdims=True)
            probabilities[np.arange(len(batch)), batch_targets] -= 1.0
            probabilities *= (batch_weights / normalization)[:, None]

            cat_gradient = np.zeros_like(categorical_weights)
            for slot in range(CAT_FEATURES):
                np.add.at(cat_gradient, cats[:, slot], probabilities)
            cat_gradient[1:] += regularization * categorical_weights[1:]
            numeric_gradient = nums.T @ probabilities + regularization * numeric_weights
            bias_gradient = probabilities.sum(axis=0)
            step += 1
            for parameter, gradient, m, v in zip(
                parameters,
                (cat_gradient, numeric_gradient, bias_gradient),
                first,
                second,
            ):
                _weighted_adam_update(
                    parameter, gradient, m, v, step, learning_rate
                )
    return categorical_weights, numeric_weights, bias


def _fit_neural(
    categories: np.ndarray,
    numeric: np.ndarray,
    targets: np.ndarray,
    weights: np.ndarray,
    class_count: int,
    epochs: int,
    seed: int,
    hidden_size: int = 24,
) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    """Small embedding-sum -> tanh -> softmax classifier implemented in NumPy."""
    rng = np.random.default_rng(seed)
    embeddings = rng.normal(0.0, 0.035, (FEATURE_COUNT, hidden_size)).astype(np.float32)
    embeddings[0] = 0.0
    numeric_weights = rng.normal(0.0, 0.025, (NUM_FEATURES, hidden_size)).astype(
        np.float32
    )
    hidden_bias = np.zeros(hidden_size, dtype=np.float32)
    output_weights = rng.normal(0.0, 0.025, (hidden_size, class_count)).astype(
        np.float32
    )
    output_bias = np.zeros(class_count, dtype=np.float32)
    parameters = [embeddings, numeric_weights, hidden_bias, output_weights, output_bias]
    first = [np.zeros_like(p) for p in parameters]
    second = [np.zeros_like(p) for p in parameters]
    step = 0
    batch_size = 512
    learning_rate = 0.008
    regularization = 2e-5
    sample_count = len(targets)

    for _ in range(epochs):
        order = rng.permutation(sample_count)
        for start in range(0, sample_count, batch_size):
            batch = order[start : start + batch_size]
            cats = categories[batch]
            nums = numeric[batch]
            batch_targets = targets[batch]
            batch_weights = weights[batch]
            normalization = max(float(batch_weights.sum()), 1e-8)
            preactivation = embeddings[cats].sum(axis=1) + nums @ numeric_weights
            preactivation += hidden_bias
            hidden = np.tanh(preactivation)
            logits = hidden @ output_weights + output_bias
            logits -= logits.max(axis=1, keepdims=True)
            probabilities = np.exp(logits)
            probabilities /= probabilities.sum(axis=1, keepdims=True)
            probabilities[np.arange(len(batch)), batch_targets] -= 1.0
            probabilities *= (batch_weights / normalization)[:, None]

            output_gradient = hidden.T @ probabilities + regularization * output_weights
            output_bias_gradient = probabilities.sum(axis=0)
            hidden_gradient = (probabilities @ output_weights.T) * (1.0 - hidden**2)
            embedding_gradient = np.zeros_like(embeddings)
            for slot in range(CAT_FEATURES):
                np.add.at(embedding_gradient, cats[:, slot], hidden_gradient)
            embedding_gradient[0] = 0.0
            embedding_gradient[1:] += regularization * embeddings[1:]
            numeric_gradient = nums.T @ hidden_gradient + regularization * numeric_weights
            hidden_bias_gradient = hidden_gradient.sum(axis=0)
            gradients = (
                embedding_gradient,
                numeric_gradient,
                hidden_bias_gradient,
                output_gradient,
                output_bias_gradient,
            )
            step += 1
            for parameter, gradient, m, v in zip(
                parameters, gradients, first, second
            ):
                _weighted_adam_update(parameter, gradient, m, v, step, learning_rate)
            embeddings[0] = 0.0
    return embeddings, numeric_weights, hidden_bias, output_weights, output_bias


def _predict_logits(
    model: tuple[np.ndarray, ...],
    categories: np.ndarray,
    numeric: np.ndarray,
    kind: str,
) -> np.ndarray:
    results: list[np.ndarray] = []
    batch_size = 1024
    for start in range(0, len(categories), batch_size):
        cats = categories[start : start + batch_size]
        nums = numeric[start : start + batch_size]
        if kind == "linear":
            cat_weights, num_weights, bias = model
            logits = np.broadcast_to(bias, (len(cats), len(bias))).copy()
            logits += nums @ num_weights
            for slot in range(CAT_FEATURES):
                logits += cat_weights[cats[:, slot]]
        else:
            embeddings, num_weights, hidden_bias, output_weights, output_bias = model
            hidden = np.tanh(embeddings[cats].sum(axis=1) + nums @ num_weights + hidden_bias)
            logits = hidden @ output_weights + output_bias
        results.append(logits)
    if not results:
        return np.empty((0, 0), dtype=np.float32)
    return np.concatenate(results, axis=0)


def _metric_bucket(metrics: dict[str, Metric], key: str) -> Metric:
    if key not in metrics:
        metrics[key] = Metric()
    return metrics[key]


def _add_rank(
    metrics: dict[str, Metric],
    target: tuple[str, int],
    training_counts: Counter[tuple[str, int]],
    rank: int | None,
) -> None:
    bucket = "frequent" if training_counts[target] >= 10 else "rare"
    _metric_bucket(metrics, "all").add(rank)
    _metric_bucket(metrics, bucket).add(rank)


def _model_rank(logits: np.ndarray, target_id: int | None) -> int | None:
    if target_id is None:
        return None
    target_score = logits[target_id]
    return int(1 + np.count_nonzero(logits > target_score) + np.count_nonzero(logits[:target_id] == target_score))


def _subtract_four_months(value: datetime) -> datetime:
    month_index = value.year * 12 + value.month - 1 - 4
    year, month0 = divmod(month_index, 12)
    month = month0 + 1
    # SQLite's default month-shift behavior rolls an overflowing day forward
    # (for example, the 31st into the following month), rather than clamping.
    return datetime(year, month, 1) + timedelta(days=value.day - 1)


def _evaluate_baseline(
    launches: Sequence[Launch],
    hidden_packages: set[str],
    folds: Sequence[tuple[int, int]],
) -> tuple[
    dict[str, dict[str, Metric]],
    dict[str, dict[int, tuple[int, ...]]],
]:
    labels = sorted({row.label for row in launches})
    label_ids = {label: i for i, label in enumerate(labels)}
    label_count = len(labels)
    visible = np.fromiter(
        (label[0] not in hidden_packages for label in labels),
        dtype=bool,
        count=label_count,
    )
    totals = np.zeros(label_count, dtype=np.int64)
    weekdays = np.zeros((label_count, 7), dtype=np.int64)
    day_types = np.zeros((label_count, 2), dtype=np.int64)  # workday, weekend
    minutes = np.zeros((label_count, 1440), dtype=np.int32)
    history: deque[tuple[datetime, int, int, int]] = deque()
    fold_starts = {start: end for start, end in folds}
    metrics_by_fold: dict[str, dict[str, Metric]] = {}
    rankings_by_fold: dict[str, dict[int, tuple[int, ...]]] = {}
    current_fold: tuple[int, int] | None = None
    training_counts: Counter[tuple[str, int]] = Counter()

    for index, row in enumerate(launches):
        while history and history[0][0] <= _subtract_four_months(row.timestamp):
            old_time, old_label, old_minute, old_weekday = history.popleft()
            totals[old_label] -= 1
            weekdays[old_label, old_weekday] -= 1
            day_types[old_label, 1 if old_weekday in (5, 6) else 0] -= 1
            minutes[old_label, old_minute] -= 1

        if index in fold_starts:
            fold_end = fold_starts[index]
            current_fold = (index, fold_end)
            training_counts = Counter(
                prior.label
                for prior in launches[:index]
                if prior.package not in hidden_packages
            )
            metrics_by_fold[f"{index}:{fold_end}"] = {
                "all": Metric(),
                "frequent": Metric(),
                "rare": Metric(),
            }
            rankings_by_fold[f"{index}:{fold_end}"] = {}

        if current_fold and current_fold[0] <= index < current_fold[1]:
            weekday = row.timestamp.weekday()
            minute = row.timestamp.hour * 60 + row.timestamp.minute
            day_type = 1 if weekday in (5, 6) else 0
            window_indices = [(minute + offset) % 1440 for offset in range(-19, 20)]
            window_counts = minutes[:, window_indices].sum(axis=1)
            safe_totals = np.maximum(totals, 1)
            scores = window_counts * (
                weekdays[:, weekday] / safe_totals
                + day_types[:, day_type] / safe_totals
            )
            scores[~visible] = 0.0
            ranked_labels = np.flatnonzero(scores > 0.0)
            ranked_labels = ranked_labels[
                np.argsort(-scores[ranked_labels], kind="stable")
            ]
            rankings_by_fold[f"{current_fold[0]}:{current_fold[1]}"][index] = tuple(
                int(label_id) for label_id in ranked_labels
            )
            target = row.label
            if row.package not in hidden_packages:
                target_id = label_ids[target]
                target_score = scores[target_id]
                rank: int | None = None
                if target_score > 0:
                    rank = int(
                        1
                        + np.count_nonzero(scores > target_score)
                        + np.count_nonzero(scores[:target_id] == target_score)
                    )
                _add_rank(
                    metrics_by_fold[f"{current_fold[0]}:{current_fold[1]}"],
                    target,
                    training_counts,
                    rank,
                )

        label_id = label_ids[row.label]
        weekday = row.timestamp.weekday()
        minute = row.timestamp.hour * 60 + row.timestamp.minute
        totals[label_id] += 1
        weekdays[label_id, weekday] += 1
        day_types[label_id, 1 if weekday in (5, 6) else 0] += 1
        minutes[label_id, minute] += 1
        history.append((row.timestamp, label_id, minute, weekday))

    return metrics_by_fold, rankings_by_fold


def _replace_frequent_slots_with_model(
    classic_ranking: Sequence[int],
    learned_frequent_ranking: Sequence[int],
    training_counts: Counter[tuple[str, int]],
    labels: Sequence[tuple[str, int]],
    minimum_support: int = 10,
) -> tuple[int, ...]:
    """Keep SQL positions for low-support labels; fill its other slots from the model."""
    learned_index = 0
    result: list[int] = []
    for label_id in classic_ranking:
        if training_counts[labels[label_id]] < minimum_support:
            result.append(label_id)
        elif learned_index < len(learned_frequent_ranking):
            result.append(learned_frequent_ranking[learned_index])
            learned_index += 1
    result.extend(learned_frequent_ranking[learned_index:])
    return tuple(result)


def _evaluate_model_fold(
    launches: Sequence[Launch],
    categories: np.ndarray,
    numeric: np.ndarray,
    hidden_packages: set[str],
    train_end: int,
    test_start: int,
    test_end: int,
    horizon: str,
    model_kind: str,
    epochs: int,
    seed: int,
    classic_rankings: dict[int, tuple[int, ...]],
) -> tuple[dict[str, dict[str, Metric]], dict[str, int]]:
    labels = sorted({row.label for row in launches})
    label_ids = {label: index for index, label in enumerate(labels)}
    prior_counts: Counter[tuple[str, int]] = Counter(
        row.label
        for row in launches[:train_end]
        if row.package not in hidden_packages
    )
    history_mask = _horizon_mask(launches, train_end, horizon)
    train_indices = np.fromiter(
        (
            i
            for i in range(train_end)
            if history_mask[i] and launches[i].package not in hidden_packages
        ),
        dtype=np.int32,
    )
    training_counts: Counter[tuple[str, int]] = Counter(
        launches[int(i)].label for i in train_indices
    )
    classes = sorted(training_counts)
    class_ids = {label: index for index, label in enumerate(classes)}
    y = np.fromiter(
        (class_ids[launches[int(i)].label] for i in train_indices),
        dtype=np.int32,
        count=len(train_indices),
    )
    sample_weights = _sample_weights(launches, train_indices, horizon)
    if model_kind == "linear":
        model = _fit_linear(
            categories[train_indices],
            numeric[train_indices],
            y,
            sample_weights,
            len(classes),
            epochs,
            seed,
        )
    else:
        model = _fit_neural(
            categories[train_indices],
            numeric[train_indices],
            y,
            sample_weights,
            len(classes),
            epochs,
            seed,
        )

    eval_indices = [
        i
        for i in range(test_start, test_end)
        if launches[i].package not in hidden_packages
    ]
    logits = _predict_logits(
        model,
        categories[eval_indices],
        numeric[eval_indices],
        model_kind,
    )
    metrics = {
        name: {"all": Metric(), "frequent": Metric(), "rare": Metric()}
        for name in ("model", "rare-sql-fallback")
    }
    frequent_class_positions = np.fromiter(
        (
            class_id
            for class_id, label in enumerate(classes)
            if prior_counts[label] >= 10
        ),
        dtype=np.int32,
    )
    for position, launch_index in enumerate(eval_indices):
        launch = launches[launch_index]
        rank = _model_rank(logits[position], class_ids.get(launch.label))
        _add_rank(metrics["model"], launch.label, prior_counts, rank)

        frequent_order = frequent_class_positions[
            np.argsort(
                -logits[position, frequent_class_positions], kind="stable"
            )
        ]
        learned_frequent_ranking = tuple(
            label_ids[classes[int(class_id)]] for class_id in frequent_order
        )
        hybrid_ranking = _replace_frequent_slots_with_model(
            classic_rankings[launch_index],
            learned_frequent_ranking,
            prior_counts,
            labels,
        )
        try:
            hybrid_rank = hybrid_ranking.index(label_ids[launch.label]) + 1
        except ValueError:
            hybrid_rank = None
        _add_rank(
            metrics["rare-sql-fallback"], launch.label, prior_counts, hybrid_rank
        )
    return metrics, {
        "train_examples": len(train_indices),
        "labels": len(classes),
        "rare_labels": sum(count < 10 for count in training_counts.values()),
    }


def evaluate(
    launches: Sequence[Launch],
    hidden_packages: set[str],
    linear_epochs: int = 4,
    neural_epochs: int = 6,
) -> Evaluation:
    n = len(launches)
    first = int(n * 0.60)
    second = int(n * 0.80)
    folds = [(first, second), (second, n)]
    if first < 2 or second <= first or n <= second:
        raise ValueError("At least five chronological launch rows are required")
    categories, numeric = _prepare_features(launches)
    all_metrics: dict[str, dict[str, dict[str, Metric]]] = {}
    train_counts_summary: dict[str, dict[str, int]] = {}
    fold_ranges: list[dict[str, object]] = []
    baseline_metrics, baseline_rankings = _evaluate_baseline(
        launches, hidden_packages, folds
    )
    fold_keys = list(baseline_metrics)
    for fold_number, (test_start, test_end) in enumerate(folds, start=1):
        key = fold_keys[fold_number - 1]
        all_metrics[f"fold-{fold_number}"] = {"classic": baseline_metrics[key]}
        fold_ranges.append(
            {
                "name": f"fold-{fold_number}",
                "train_rows": sum(
                    launches[i].package not in hidden_packages
                    for i in range(test_start)
                ),
                "training_end": launches[test_start - 1].timestamp.date(),
                "test_start": launches[test_start].timestamp.date(),
                "test_end": launches[test_end - 1].timestamp.date(),
                "test_rows": sum(
                    launches[i].package not in hidden_packages
                    for i in range(test_start, test_end)
                ),
            }
        )
        for model_kind in MODEL_NAMES:
            epochs = linear_epochs if model_kind == "linear" else neural_epochs
            for horizon_number, horizon in enumerate(HORIZONS):
                candidate = f"{model_kind}/{horizon}"
                print(
                    f"Evaluating fold {fold_number}/2, {model_kind}, {horizon}...",
                    file=sys.stderr,
                )
                metrics, summary = _evaluate_model_fold(
                    launches,
                    categories,
                    numeric,
                    hidden_packages,
                    test_start,
                    test_start,
                    test_end,
                    horizon,
                    model_kind,
                    epochs,
                    seed=1701 + fold_number * 101 + horizon_number * 13 + (model_kind == "neural"),
                    classic_rankings=baseline_rankings[key],
                )
                all_metrics[f"fold-{fold_number}"][candidate] = metrics["model"]
                all_metrics[f"fold-{fold_number}"][
                    f"{candidate}+rare-sql-fallback"
                ] = metrics["rare-sql-fallback"]
                train_counts_summary[f"fold-{fold_number}/{candidate}"] = summary
    return Evaluation(all_metrics, train_counts_summary, fold_ranges)


def _aggregate_metrics(
    metrics: dict[str, dict[str, dict[str, Metric]]], candidate: str
) -> dict[str, Metric]:
    result = {bucket: Metric() for bucket in ("all", "frequent", "rare")}
    for fold in metrics.values():
        for bucket in result:
            result[bucket].merge(fold[candidate][bucket])
    return result


def _format_metric(metric: Metric) -> str:
    return f"{metric.hit_rate:.3f} / {metric.mrr:.3f} (n={metric.count:,})"


def render_report(
    audit: dict[str, object], evaluation: Evaluation, linear_epochs: int, neural_epochs: int
) -> str:
    model_candidates = [
        f"{model}/{horizon}" for model in MODEL_NAMES for horizon in HORIZONS
    ]
    fallback_candidates = [
        f"{candidate}+rare-sql-fallback" for candidate in model_candidates
    ]
    candidates = ["classic"] + model_candidates + fallback_candidates
    aggregate = {
        candidate: _aggregate_metrics(evaluation.metrics, candidate)
        for candidate in candidates
    }
    baseline_dev = evaluation.metrics["fold-1"]["classic"]
    baseline_late = evaluation.metrics["fold-2"]["classic"]["all"]
    baseline_late_rare = evaluation.metrics["fold-2"]["classic"]["rare"]
    viable = []
    for candidate in fallback_candidates:
        development = evaluation.metrics["fold-1"][candidate]
        if (
            development["all"].hit_rate >= baseline_dev["all"].hit_rate - 0.02
            and development["all"].mrr >= baseline_dev["all"].mrr - 0.02
            and development["rare"].hit_rate >= baseline_dev["rare"].hit_rate - 0.02
            and development["rare"].mrr >= baseline_dev["rare"].mrr - 0.02
        ):
            viable.append(candidate)
    development_choice: str | None = None
    if viable:
        development_choice = max(
            viable,
            key=lambda candidate: (
                evaluation.metrics["fold-1"][candidate]["all"].hit_rate,
                evaluation.metrics["fold-1"][candidate]["all"].mrr,
            ),
        )
    selected: str | None = None
    if development_choice is not None:
        final = evaluation.metrics["fold-2"][development_choice]
        if (
            final["all"].hit_rate >= baseline_late.hit_rate - 0.02
            and final["all"].mrr >= baseline_late.mrr - 0.02
            and final["rare"].hit_rate >= baseline_late_rare.hit_rate - 0.02
            and final["rare"].mrr >= baseline_late_rare.mrr - 0.02
        ):
            selected = development_choice

    count_bins = audit["label_count_bins"]
    event_bins = audit["label_event_bins"]
    total = int(audit["launches"])
    lines = [
        f"# {REPORT_TITLE}",
        "",
        "This report contains aggregate counts and metrics only. Package names, profile IDs, "
        "Wi-Fi values, geohashes, and coordinates are never emitted.",
        "",
        "## History audit",
        "",
        f"- Valid launch rows: **{total:,}**; date range: **{audit['start']} through {audit['end']}**.",
        f"- App/profile labels: **{audit['app_profile_labels']:,}** across **{audit['profiles']}** profiles.",
        "- Launch counts per profile (min / median / max): "
        f"**{audit['profile_launch_min']:,} / {audit['profile_launch_median']:,.0f} / {audit['profile_launch_max']:,}**.",
        "- App/profile frequency (label count; corresponding launch count): "
        + "; ".join(
            f"{bucket}: {count_bins.get(bucket, 0):,} labels / {event_bins.get(bucket, 0):,} launches"
            for bucket in ("1", "2–9", "10–99", "100+")
        )
        + ".",
        f"- Wi-Fi: **{audit['wifi_known_rows']:,} ({_percent(audit['wifi_known_rows'], total)})** connected contexts with an SSID; "
        f"**{audit['wifi_no_wifi_rows']:,} ({_percent(audit['wifi_no_wifi_rows'], total)})** known NO_WIFI; "
        f"**{audit['wifi_unknown_rows']:,} ({_percent(audit['wifi_unknown_rows'], total)})** unknown. "
        "Unknown is omitted from features; legacy retained SSIDs are treated as connected evidence.",
        f"- Location: coordinates on **{audit['coordinate_rows']:,} ({_percent(audit['coordinate_rows'], total)})** rows; "
        f"coarse geohash available on **{audit['geohash_rows']:,} ({_percent(audit['geohash_rows'], total)})** rows "
        f"and **{audit['coarse_location_cells']:,}** distinct five-character cells.",
        f"- Distinct non-null Wi-Fi contexts: **{audit['distinct_wifi_contexts']:,}** (values are not reported).",
        "",
        "## Evaluation protocol",
        "",
        "- Rows are ordered chronologically (timestamp, then database row ID when present); hidden-from-Top packages are excluded from labels and scoring, matching Home filtering.",
        "- Two expanding walk-forward folds: first train on the earliest 60% and test on 60–80%; then train on the earliest 80% and test on 80–100%. No row is randomly split across train/test.",
        "- Each learned model is fitted once at its fold cutoff and held fixed for that test block; the next fold refits using its larger earlier-history prefix. Test examples never train their own prediction model.",
        "- Frequent labels have at least 10 training launches; rare labels have fewer than 10, including labels not yet observed in training.",
        "- Metrics are event-weighted HitRate@6 and MRR of the next launch. An unranked target contributes zero to both.",
        "- Classic reproduces the current Home SQL score using only prior launches in its rolling four-calendar-month window, exact weekday/workday/weekend ratios, and the ±19-minute bins implied by the SQL's `< 20` minute condition. It does not use Wi-Fi or location.",
        "- Explicit fallback candidate: preserve each classic-ranked label with fewer than 10 prior training launches in its exact SQL rank slot; replace only frequent-label SQL slots with the learned model's frequent-label order, then append remaining learned frequent labels. Labels with no positive classic score remain unranked by SQL. This avoids target-aware fallback and keeps the SQL rare-label coverage measurable.",
        "- Learned features: profile, weekday, half-hour, cyclic clock/year, coarse five-character geohash, connected SSID, and explicit known NO_WIFI. UNKNOWN Wi-Fi and missing location are omitted. Linear training epochs: "
        f"{linear_epochs}; neural epochs: {neural_epochs}. NumPy is the offline training/evaluation runtime.",
        "- Horizons: all prior history; rolling 2 years; rolling 1 year; and all history with a one-year exponential half-life.",
        "",
        "| Fold | Prior training rows | Train through | Test interval | Scored test launches |",
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
            "## Aggregate results",
            "",
            "Each metric cell is **HitRate@6 / MRR (events)**. Aggregate values combine both test folds.",
            "",
            "| Candidate | All labels | Frequent labels | Rare labels |",
            "|---|---:|---:|---:|",
        ]
    )
    for candidate in candidates:
        metrics = aggregate[candidate]
        display_name = "Current Home SQL" if candidate == "classic" else candidate
        lines.append(
            f"| {display_name} | {_format_metric(metrics['all'])} | "
            f"{_format_metric(metrics['frequent'])} | {_format_metric(metrics['rare'])} |"
        )
    lines.extend(
        [
            "",
            "## Fold-1 development results",
            "",
            "This fold is used for candidate selection. A candidate must keep overall and rare-label HitRate@6 and MRR within 0.020 absolute of classic; among eligible candidates, choose highest overall HitRate@6, then MRR. Frequent-label results are shown but do not substitute for rare-label safety.",
            "",
            "| Candidate | All labels | Frequent labels | Rare labels |",
            "|---|---:|---:|---:|",
        ]
    )
    for candidate in candidates:
        development = evaluation.metrics["fold-1"][candidate]
        display_name = "Current Home SQL" if candidate == "classic" else candidate
        lines.append(
            f"| {display_name} | {_format_metric(development['all'])} | "
            f"{_format_metric(development['frequent'])} | "
            f"{_format_metric(development['rare'])} |"
        )
    lines.extend(
        [
            "",
            "## Fold-2 later-period verification",
            "",
            "Fold 2 is a later-period verification only, not used to choose the candidate. Acceptance requires the same overall and rare-label non-regression checks used in fold 1.",
            "",
            "| Candidate | Overall HitRate@6 / MRR | Frequent HitRate@6 / MRR | Rare HitRate@6 / MRR |",
            "|---|---:|---:|---:|",
        ]
    )
    for candidate in candidates:
        late = evaluation.metrics["fold-2"][candidate]
        display_name = "Current Home SQL" if candidate == "classic" else candidate
        lines.append(
            f"| {display_name} | {late['all'].hit_rate:.3f} / {late['all'].mrr:.3f} "
            f"(n={late['all'].count:,}) | {late['frequent'].hit_rate:.3f} / "
            f"{late['frequent'].mrr:.3f} (n={late['frequent'].count:,}) | {late['rare'].hit_rate:.3f} / "
            f"{late['rare'].mrr:.3f} (n={late['rare'].count:,}) |"
        )
    lines.extend(["", "## Decision", ""])
    if selected is None:
        if development_choice is None:
            lines.extend(
                [
                    "**Decision: no learned-model-plus-SQL-fallback candidate passed the fold-1 development guard on both overall and rare labels. No learned model/runtime/horizon is selected.**",
                    "",
                    "Fold 2 is shown as later-period verification only. Keep the Room/SQLite classic ranking as the production strategy (four-calendar-month history); investigate a context-generalization or data-sparsity experiment before another model-selection attempt.",
                ]
            )
        else:
            final = evaluation.metrics["fold-2"][development_choice]
            lines.extend(
                [
                    f"**Decision: {development_choice} passed fold-1 development but failed fold-2 verification. No learned model/runtime/horizon is selected.**",
                    "",
                    f"Fold-2 overall: HitRate@6 {final['all'].hit_rate:.3f} vs classic {baseline_late.hit_rate:.3f}; MRR {final['all'].mrr:.3f} vs {baseline_late.mrr:.3f}. Rare labels: {final['rare'].hit_rate:.3f} / {final['rare'].mrr:.3f} vs classic {baseline_late_rare.hit_rate:.3f} / {baseline_late_rare.mrr:.3f}.",
                    "Keep the Room/SQLite classic ranking as the production strategy (four-calendar-month history); revisit selection after a new context-generalization or data-sparsity experiment.",
                ]
            )
    else:
        selected_late = evaluation.metrics["fold-2"][selected]
        baseline_frequent = evaluation.metrics["fold-2"]["classic"]["frequent"]
        baseline_rare = evaluation.metrics["fold-2"]["classic"]["rare"]
        candidate_frequent = selected_late["frequent"]
        candidate_rare = selected_late["rare"]
        model_kind, horizon = selected.split("/")
        lines.extend(
            [
                f"**Selected: {model_kind} classifier with {horizon} training history.** It passes the fold-2 overall-and-rare non-regression guard.",
                f"- Fold-2 overall: HitRate@6 **{selected_late['all'].hit_rate:.3f}** vs classic **{baseline_late.hit_rate:.3f}**; MRR **{selected_late['all'].mrr:.3f}** vs **{baseline_late.mrr:.3f}**.",
                f"- Fold-2 frequent labels: HitRate@6 **{candidate_frequent.hit_rate:.3f}** vs **{baseline_frequent.hit_rate:.3f}**; MRR **{candidate_frequent.mrr:.3f}** vs **{baseline_frequent.mrr:.3f}** (n={candidate_frequent.count:,}).",
                f"- Fold-2 rare labels: HitRate@6 **{candidate_rare.hit_rate:.3f}** vs **{baseline_rare.hit_rate:.3f}**; MRR **{candidate_rare.mrr:.3f}** vs **{baseline_rare.mrr:.3f}** (n={candidate_rare.count:,}).",
                "- Production runtime recommendation: deterministic pure-Kotlin CPU training/inference of the selected small classifier; the NumPy dependency is only for this offline backtest. No Android ML runtime is required.",
            ]
        )
    lines.extend(
        [
            "",
            "## Reproduction",
            "",
            "From a clean checkout, install the single offline-evaluation dependency and provide a local Room database export (the personal export is not a build input):",
            "",
            "```sh",
            "python3 -m pip install -r tools/requirements-home-selection-eval.txt",
            "python3 tools/evaluate_home_app_selection.py \\",
            "  --database /local/path/to/neurhome_database_sample.db \\",
            "  --output /tmp/home-app-selection-report.md",
            "```",
            "",
            "The database is opened read-only. The report contains no app/profile identifiers, SSIDs, geohashes, or coordinates.",
            "",
        ]
    )
    return "\n".join(lines)


def _percent(count: int, total: int) -> str:
    return f"{100.0 * count / total:.1f}%" if total else "0.0%"


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", required=True, type=Path, help="local Room database path")
    parser.add_argument("--output", type=Path, help="optional Markdown report path")
    parser.add_argument("--linear-epochs", type=int, default=4)
    parser.add_argument("--neural-epochs", type=int, default=6)
    args = parser.parse_args(argv)
    if args.linear_epochs < 1 or args.neural_epochs < 1:
        parser.error("epoch counts must be positive")
    try:
        launches, hidden_packages = load_database(args.database)
        audit = audit_history(launches)
        evaluation = evaluate(
            launches,
            hidden_packages,
            linear_epochs=args.linear_epochs,
            neural_epochs=args.neural_epochs,
        )
        report = render_report(
            audit, evaluation, args.linear_epochs, args.neural_epochs
        ).rstrip()
        if args.output:
            args.output.write_text(report + "\n", encoding="utf-8")
            print(f"Wrote aggregate report: {args.output}")
        else:
            print(report)
    except (OSError, ValueError, sqlite3.Error) as exc:
        print(f"Evaluation failed: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":  # pragma: no cover - command-line entry point
    raise SystemExit(main())
