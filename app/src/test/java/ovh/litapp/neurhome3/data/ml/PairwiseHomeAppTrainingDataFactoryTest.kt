package ovh.litapp.neurhome3.data.ml

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.litapp.neurhome3.data.models.ApplicationLogEntry
import ovh.litapp.neurhome3.data.models.WifiContextState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * [PairwiseHomeAppTrainingDataFactory] replays launch history into leakage-free pairwise
 * examples. The existing "prior-only" reranker test passes even for a leaky factory, so these
 * tests assert the factory's own guarantees: no future launches, hidden packages excluded, the
 * four-month window, and a deterministic candidate cap.
 */
class PairwiseHomeAppTrainingDataFactoryTest {
    @Test
    fun hiddenPackagesNeverBecomeTargetsOrCandidates() = runBlocking {
        val day = LocalDateTime.of(2025, 1, 1, 9, 0)
        val launches = buildList {
            repeat(12) { index ->
                val at = day.plusDays(index.toLong())
                add(launch(index * 3, "synthetic-hidden", at))
                add(launch(index * 3 + 1, "synthetic-visible-a", at))
                add(launch(index * 3 + 2, "synthetic-visible-b", at))
            }
        }

        val examples = PairwiseHomeAppTrainingDataFactory.build(
            launches = launches,
            hiddenPackages = setOf("synthetic-hidden")
        )

        assertFalse(examples.isEmpty())
        assertTrue(examples.none { it.target.packageName == "synthetic-hidden" })
        assertTrue(examples.none { example ->
            example.classicCandidates.any { it.label.packageName == "synthetic-hidden" }
        })
    }

    @Test
    fun packagesFirstSeenAfterAQueryNeverAppearInThatQuery() = runBlocking {
        val january = LocalDateTime.of(2025, 1, 1, 9, 0)
        val february = LocalDateTime.of(2025, 2, 1, 9, 0)
        val launches = buildList {
            repeat(12) { index ->
                val early = january.plusDays(index.toLong())
                add(launch(index * 3, "synthetic-early-a", early))
                add(launch(index * 3 + 1, "synthetic-early-b", early))
                add(launch(index * 3 + 2, "synthetic-late", february.plusDays(index.toLong())))
            }
        }

        val examples = PairwiseHomeAppTrainingDataFactory.build(launches, hiddenPackages = emptySet())

        val beforeLateIsSeen = examples.filter { it.timestamp.isBefore(february) }
        assertFalse(beforeLateIsSeen.isEmpty())
        assertTrue(beforeLateIsSeen.none { example ->
            example.target.packageName == "synthetic-late" ||
                example.classicCandidates.any { it.label.packageName == "synthetic-late" }
        })
        // Once "synthetic-late" does have history it shows up, so the guard above is not vacuous.
        assertTrue(examples.any { example ->
            example.classicCandidates.any { it.label.packageName == "synthetic-late" }
        })
    }

    @Test
    fun launchesOlderThanTheFourMonthWindowArePruned() = runBlocking {
        val targetTime = LocalDateTime.of(2025, 7, 14, 9, 0)
        // fourCalendarMonthsBefore(targetTime): the window start is inclusive, so a launch at
        // exactly this instant is already pruned while one a second later survives. 2025-03-14
        // and 2025-07-14 are both weekdays, so a retained launch would score as a candidate.
        val cutoff = LocalDateTime.of(2025, 3, 14, 9, 0)
        val launches = buildList {
            repeat(12) { index ->
                add(launch(index, "synthetic-old", cutoff.minusDays(11 - index.toLong())))
                add(launch(100 + index, "synthetic-boundary", cutoff))
                add(launch(200 + index, "synthetic-kept", cutoff.plusSeconds(1)))
                add(
                    launch(
                        300 + index,
                        "synthetic-recent",
                        LocalDateTime.of(2025, 6, 10, 9, 0).plusDays(index.toLong())
                    )
                )
            }
            add(launch(400, "synthetic-recent", targetTime))
        }

        val examples = PairwiseHomeAppTrainingDataFactory.build(launches, hiddenPackages = emptySet())
        val candidates = examples.last { it.timestamp == targetTime }
            .classicCandidates
            .map { it.label.packageName }
            .toSet()

        assertFalse(candidates.contains("synthetic-old"))
        assertFalse(candidates.contains("synthetic-boundary"))
        assertTrue(candidates.contains("synthetic-kept"))
        assertTrue(candidates.contains("synthetic-recent"))
    }

    @Test
    fun candidateLimitKeepsTheTopScoringLabelsWithADeterministicTieBreak() = runBlocking {
        val start = LocalDateTime.of(2025, 1, 1, 9, 0)
        val targetTime = start.plusDays(12)
        val launches = buildList {
            repeat(12) { index ->
                val at = start.plusDays(index.toLong())
                add(launch(index * 3, "synthetic-a", at))
                add(launch(index * 3 + 1, "synthetic-b", at))
                add(launch(index * 3 + 2, "synthetic-c", at))
            }
            add(launch(400, "synthetic-a", targetTime))
        }

        val examples = PairwiseHomeAppTrainingDataFactory.build(
            launches = launches,
            hiddenPackages = emptySet(),
            candidateLimit = 2
        )

        // All three labels score identically, so the tie-break is the sorted label id.
        assertEquals(
            listOf("synthetic-a", "synthetic-b"),
            examples.last { it.timestamp == targetTime }.classicCandidates.map { it.label.packageName }
        )
    }

    @Test
    fun offsetFormTimestampsDropThoseLaunchesFromTheReplay() = runBlocking {
        val day = LocalDateTime.of(2025, 1, 1, 9, 0)
        fun launchesOf(rawTimestamp: (LocalDateTime) -> String) = (0 until 24).map { index ->
            val at = day.plusDays((index % 12).toLong())
            launch(
                uid = index,
                packageName = if (index < 12) "synthetic-a" else "synthetic-b",
                timestamp = at,
                rawTimestamp = rawTimestamp(at)
            )
        }

        val offsetLaunches = launchesOf { it.toString() + "+02:00" }
        val isoLaunches = launchesOf { it.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) }

        assertTrue(
            PairwiseHomeAppTrainingDataFactory.build(offsetLaunches, hiddenPackages = emptySet())
                .isEmpty()
        )
        assertFalse(
            PairwiseHomeAppTrainingDataFactory.build(isoLaunches, hiddenPackages = emptySet())
                .isEmpty()
        )
    }

    private fun launch(
        uid: Int,
        packageName: String,
        timestamp: LocalDateTime,
        rawTimestamp: String = timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    ) = ApplicationLogEntry(
        uid = uid,
        packageName = packageName,
        timestamp = rawTimestamp,
        wifi = null,
        wifiState = WifiContextState.UNKNOWN,
        latitude = null,
        longitude = null,
        geohash = null,
        user = 0
    )
}
