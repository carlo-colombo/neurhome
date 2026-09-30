import importlib.util
import contextlib
import io
import sqlite3
import sys
import tempfile
import unittest
from datetime import datetime, timedelta
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "evaluate_home_app_selection.py"
SPEC = importlib.util.spec_from_file_location("home_selection_eval", SCRIPT)
evaluator = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = evaluator
SPEC.loader.exec_module(evaluator)


class EvaluationHarnessTest(unittest.TestCase):
    def test_null_wifi_and_location_are_omitted_from_features(self):
        row = evaluator.Launch(
            package="private-app-name",
            profile=7,
            timestamp=datetime(2024, 2, 3, 4, 5),
            wifi=None,
            latitude=None,
            longitude=None,
            geohash=None,
        )
        categories, _ = evaluator.encode_context(row)
        self.assertEqual(sum(index != 0 for index in categories), 3)
        self.assertEqual(
            evaluator.audit_history([row])["wifi_unknown_rows"], 1
        )

    def test_known_no_wifi_is_distinct_from_unknown(self):
        unknown = evaluator.Launch(
            package="synthetic-app",
            profile=0,
            timestamp=datetime(2024, 2, 3, 4, 5),
            wifi=None,
            latitude=None,
            longitude=None,
            geohash=None,
            wifi_state="UNKNOWN",
        )
        no_wifi = evaluator.Launch(
            package="synthetic-app",
            profile=0,
            timestamp=datetime(2024, 2, 3, 4, 5),
            wifi=None,
            latitude=None,
            longitude=None,
            geohash=None,
            wifi_state="NO_WIFI",
        )

        unknown_categories, _ = evaluator.encode_context(unknown)
        no_wifi_categories, _ = evaluator.encode_context(no_wifi)

        self.assertEqual(sum(index != 0 for index in unknown_categories), 3)
        self.assertEqual(sum(index != 0 for index in no_wifi_categories), 4)
        self.assertEqual(evaluator.audit_history([no_wifi])["wifi_no_wifi_rows"], 1)

    def test_null_wifi_is_unknown_and_report_never_contains_raw_values(self):
        with tempfile.TemporaryDirectory() as directory:
            database = Path(directory) / "synthetic.db"
            connection = sqlite3.connect(database)
            connection.executescript(
                """
                CREATE TABLE ApplicationLogEntry (
                    uid INTEGER PRIMARY KEY, packageName TEXT NOT NULL,
                    timestamp TEXT NOT NULL, wifi TEXT, latitude REAL,
                    longitude REAL, geohash TEXT, user INTEGER NOT NULL
                );
                CREATE TABLE AdditionalPackageMetadata (
                    packageName TEXT NOT NULL, hideFrom TEXT
                );
                """
            )
            start = datetime(2024, 1, 1)
            for index in range(100):
                day = start + timedelta(days=index)
                package = f"PRIVATE_APP_{index % 4}"
                wifi = "PRIVATE_SSID" if index % 3 else None
                geohash = "PRIVATE_GEOHASH" if index % 2 else None
                connection.execute(
                    "INSERT INTO ApplicationLogEntry VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    (
                        index + 1,
                        package,
                        day.isoformat(sep=" "),
                        wifi,
                        1.0 if geohash else None,
                        2.0 if geohash else None,
                        geohash,
                        index % 2,
                    ),
                )
            connection.commit()
            connection.close()

            launches, hidden = evaluator.load_database(database)
            audit = evaluator.audit_history(launches)
            with contextlib.redirect_stderr(io.StringIO()):
                evaluation = evaluator.evaluate(
                    launches, hidden, linear_epochs=1, neural_epochs=1
                )
            report = evaluator.render_report(audit, evaluation, 1, 1)

        self.assertEqual(audit["wifi_unknown_rows"], 34)
        self.assertEqual(audit["wifi_known_rows"], 66)
        self.assertNotIn("PRIVATE_APP", report)
        self.assertNotIn("PRIVATE_SSID", report)
        self.assertNotIn("PRIVATE_GEOHASH", report)
        self.assertIn("walk-forward", report)
        self.assertIn("Rare labels", report)

    def test_metrics_have_zero_for_unranked_and_reciprocal_rank(self):
        metric = evaluator.Metric()
        metric.add(None)
        metric.add(2)
        self.assertEqual(metric.count, 2)
        self.assertEqual(metric.hits, 1)
        self.assertAlmostEqual(metric.mrr, 0.25)

    def test_low_support_sql_slot_is_preserved_when_frequent_slots_are_replaced(self):
        labels = [
            ("synthetic-frequent-a", 0),
            ("synthetic-rare", 0),
            ("synthetic-frequent-b", 0),
        ]
        training_counts = evaluator.Counter(
            {labels[0]: 12, labels[1]: 4, labels[2]: 20}
        )

        ranking = evaluator._replace_frequent_slots_with_model(
            classic_ranking=(0, 1, 2),
            learned_frequent_ranking=(2, 0),
            training_counts=training_counts,
            labels=labels,
        )

        self.assertEqual(ranking, (2, 1, 0))
        self.assertEqual(ranking.index(1), 1)

    def test_low_support_label_without_sql_score_stays_unranked(self):
        labels = [("synthetic-frequent", 0), ("synthetic-rare", 0)]
        training_counts = evaluator.Counter({labels[0]: 11, labels[1]: 2})

        ranking = evaluator._replace_frequent_slots_with_model(
            classic_ranking=(0,),
            learned_frequent_ranking=(0,),
            training_counts=training_counts,
            labels=labels,
        )

        self.assertEqual(ranking, (0,))

    def test_four_month_cutoff_matches_sqlite_calendar_shift(self):
        connection = sqlite3.connect(":memory:")
        for value in ("2024-03-31", "2024-06-30", "2025-09-30", "2026-01-31"):
            expected = connection.execute(
                "SELECT date(?, '-4 months')", (value,)
            ).fetchone()[0]
            actual = evaluator._subtract_four_months(
                datetime.fromisoformat(value + " 18:30:00")
            ).date().isoformat()
            self.assertEqual(actual, expected)
        connection.close()


if __name__ == "__main__":
    unittest.main()
