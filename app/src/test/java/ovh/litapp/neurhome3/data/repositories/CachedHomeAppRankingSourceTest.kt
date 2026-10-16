package ovh.litapp.neurhome3.data.repositories

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pins that the ranking reads are cached against the inputs they are computed from: the launch-log
 * version, the top-apps visibility version, and the local time bucket of the query. A read that no
 * input moved must not reach the four-month CTE again.
 */
class CachedHomeAppRankingSourceTest {
    private val minute = LocalDateTime.of(2025, 4, 2, 9, 30, 15)
    private var launchLogVersion = 7L
    private var visibilityVersion = 3L
    private var now = minute

    private val delegate = CountingHomeAppRankingSource()
    private val source = CachedHomeAppRankingSource(
        delegate = delegate,
        launchLogVersion = { launchLogVersion },
        topAppsVisibilityVersion = { visibilityVersion },
        now = { now },
    )

    @Test
    fun repeatedScoreReadsInsideTheSameMinuteAreServedFromCache() {
        val first = source.topAppsByScore(limit = 64)
        val second = source.topAppsByScore(limit = 64)

        assertSame(first, second)
        assertEquals(1, delegate.scoreReads)
    }

    @Test
    fun theMinuteBucketTheQueryIsKeyedOnReReadsTheScore() {
        source.topAppsByScore(limit = 64)
        now = minute.plusSeconds(10)
        source.topAppsByScore(limit = 64)
        // Still the same minute.
        assertEquals(1, delegate.scoreReads)

        now = minute.plusMinutes(1)
        source.topAppsByScore(limit = 64)
        assertEquals(2, delegate.scoreReads)
    }

    @Test
    fun aLaunchOrAVisibilityChangeReReadsTheScore() {
        source.topAppsByScore(limit = 64)
        launchLogVersion++
        source.topAppsByScore(limit = 64)
        visibilityVersion++
        source.topAppsByScore(limit = 64)

        assertEquals(3, delegate.scoreReads)
    }

    @Test
    fun aDifferentLimitIsNotServedFromTheCachedRead() {
        source.topAppsByScore(limit = 64)
        source.topAppsByScore(limit = 32)

        assertEquals(2, delegate.scoreReads)
    }

    @Test
    fun launchCountsAreCachedForTheWholeDayAndReReadOnALaunch() {
        val first = source.mostUsedApps()
        val second = source.mostUsedApps()
        assertSame(first, second)
        now = minute.plusHours(9)
        source.mostUsedApps()
        assertEquals(1, delegate.mostUsedReads)

        launchLogVersion++
        source.mostUsedApps()
        now = minute.toLocalDate().plusDays(1).atStartOfDay()
        source.mostUsedApps()

        assertEquals(3, delegate.mostUsedReads)
    }

    @Test
    fun quietProfilesAreNeverCachedHere() {
        source.quietProfileIds()
        source.quietProfileIds()

        assertEquals(2, delegate.quietReads)
    }
}

private class CountingHomeAppRankingSource : HomeAppRankingSource {
    var scoreReads = 0
    var mostUsedReads = 0
    var quietReads = 0

    override fun topAppsByScore(limit: Int): List<HomeAppCandidate> {
        scoreReads++
        return listOf(HomeAppCandidate("first", user = 0, score = 3.0))
    }

    override fun mostUsedApps(): List<HomeAppCandidate> {
        mostUsedReads++
        return listOf(HomeAppCandidate("first", user = 0, score = 5.0))
    }

    override fun quietProfileIds(): Set<Int> {
        quietReads++
        return emptySet()
    }
}
