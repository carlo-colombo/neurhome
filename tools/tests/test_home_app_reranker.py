import sys
import unittest
from collections import Counter
from datetime import datetime, timedelta
from pathlib import Path

import numpy as np


TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

import evaluate_home_app_reranker as reranker
from evaluate_home_app_selection import Launch, FEATURE_COUNT, NUM_FEATURES


class PairwiseRerankerTest(unittest.TestCase):
    def test_pairwise_training_is_deterministic_and_learns_preference(self):
        categories = np.asarray([[1, 2, 3, 0, 0]] * 24, dtype=np.int32)
        numeric = np.zeros((24, NUM_FEATURES), dtype=np.float32)
        examples = [
            reranker.PairwiseExample(index, 0, (0, 1))
            for index in range(len(categories))
        ]
        weights = np.ones(len(examples), dtype=np.float32)
        config = reranker.RerankerConfig("full", 2, 3, 0.08, 0.0001)

        first = reranker._fit_pairwise(
            examples, weights, categories, numeric, 2, config, seed=51
        )
        second = reranker._fit_pairwise(
            examples, weights, categories, numeric, 2, config, seed=51
        )

        self.assertTrue(np.array_equal(first.categorical, second.categorical))
        self.assertTrue(np.array_equal(first.numeric, second.numeric))
        self.assertTrue(np.array_equal(first.bias, second.bias))
        self.assertGreater(first.bias[0], first.bias[1])
        self.assertEqual(first.categorical.shape, (2, FEATURE_COUNT))

    def test_reranker_uses_profile_aware_support_and_drops_rare_candidates(self):
        labels = [("same-app", 0), ("same-app", 10), ("other-app", 0)]
        counts = Counter({labels[0]: 10, labels[1]: 3, labels[2]: 12})
        model = reranker.PairwiseModel(
            categorical=np.zeros((3, FEATURE_COUNT), dtype=np.float32),
            numeric=np.zeros((3, NUM_FEATURES), dtype=np.float32),
            bias=np.asarray([0.0, 100.0, 1.0], dtype=np.float32),
            classic_rank_weight=0.0,
        )

        ranking = reranker._rerank_frequent_candidates(
            classic_ranking=(0, 1, 2),
            training_counts=counts,
            labels=labels,
            categories=np.asarray([[1, 2, 3, 0, 0]], dtype=np.int32),
            numeric=np.zeros((1, NUM_FEATURES), dtype=np.float32),
            context_index=0,
            model=model,
            candidate_count=2,
        )

        # The low-support profile variant is omitted; the frequent target order
        # is learned without conflating the two profiles of the same package.
        self.assertEqual(ranking, (2, 0))

    def test_training_examples_replay_only_prior_supported_classic_candidates(self):
        start = datetime(2025, 1, 1, 9, 0)
        launches = []
        for index in range(24):
            package = "synthetic-alpha" if index < 12 else "synthetic-beta"
            launches.append(
                Launch(
                    package=package,
                    profile=0,
                    timestamp=start + timedelta(days=index),
                    wifi=None,
                    latitude=None,
                    longitude=None,
                    geohash=None,
                )
            )

        examples = reranker._build_training_examples(
            launches, hidden_packages=set(), train_end=len(launches), max_candidates=8
        )

        beta_id = sorted({row.label for row in launches}).index(("synthetic-beta", 0))
        self.assertTrue(examples)
        self.assertTrue(any(example.target_id == beta_id for example in examples))
        self.assertTrue(all(example.launch_index < len(launches) for example in examples))
        self.assertTrue(all(len(example.candidates) >= 2 for example in examples))

    def test_automatic_grid_is_repeatable_and_covers_tuning_dimensions(self):
        first = reranker._configuration_grid()
        second = reranker._configuration_grid()

        self.assertEqual(first, second)
        self.assertEqual(len(first), 96)
        self.assertEqual({config.horizon for config in first}, set(reranker.HORIZONS))
        self.assertEqual({config.candidate_count for config in first}, {6, 12, 24})
        self.assertEqual({config.epochs for config in first}, {1, 2})
        self.assertEqual({config.learning_rate for config in first}, {0.03, 0.1})
        self.assertEqual({config.l2 for config in first}, {0.0001, 0.01})

    def test_context_hash_matches_kotlin_string_hash_and_masks_unknowns(self):
        self.assertEqual(reranker._java_compatible_feature_hash("abc"), 2147)
        unknown = Launch(
            package="synthetic-app",
            profile=2,
            timestamp=datetime(2024, 2, 3, 4, 5),
            wifi=None,
            latitude=None,
            longitude=None,
            geohash=None,
            wifi_state="UNKNOWN",
        )
        no_wifi = Launch(
            package="synthetic-app",
            profile=2,
            timestamp=datetime(2024, 2, 3, 4, 5),
            wifi=None,
            latitude=None,
            longitude=None,
            geohash=None,
            wifi_state="NO_WIFI",
        )

        unknown_categories, _ = reranker._prepare_reranker_features([unknown])
        no_wifi_categories, _ = reranker._prepare_reranker_features([no_wifi])

        self.assertEqual(np.count_nonzero(unknown_categories), 3)
        self.assertEqual(np.count_nonzero(no_wifi_categories), 4)

    def test_walk_forward_tuner_only_uses_configured_grid_and_verifies_leader(self):
        start = datetime(2025, 1, 1, 9, 0)
        launches = [
            Launch(
                package="synthetic-alpha" if index % 2 == 0 else "synthetic-beta",
                profile=index % 2,
                timestamp=start + timedelta(days=index),
                wifi=None,
                latitude=None,
                longitude=None,
                geohash=None,
            )
            for index in range(50)
        ]

        result = reranker.evaluate_reranker(
            launches,
            hidden_packages=set(),
            candidate_counts=(6,),
            epochs_values=(1,),
            learning_rates=(0.03,),
            l2_values=(0.001,),
            horizons=("full",),
            progress=False,
        )

        self.assertEqual(len(result.tuned_configs), 1)
        self.assertIn("classic-frequent-only", result.fold_metrics["fold-1"])
        self.assertIn("classic-frequent-only", result.fold_metrics["fold-2"])
        self.assertIn(
            result.fold1_leader.key,
            result.fold_metrics["fold-2"],
        )
        report = reranker.render_report(
            reranker.classic.audit_history(launches), result
        )
        self.assertNotIn("synthetic-alpha", report)
        self.assertNotIn("synthetic-beta", report)
        self.assertIn("searched **1** configurations", report)


if __name__ == "__main__":
    unittest.main()
