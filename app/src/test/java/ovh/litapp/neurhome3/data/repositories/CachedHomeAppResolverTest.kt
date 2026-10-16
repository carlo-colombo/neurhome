package ovh.litapp.neurhome3.data.repositories

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import ovh.litapp.neurhome3.data.Application

/**
 * Pins the resolved-row cache that lets the home ranking hand out an equal row across refreshes and
 * drop a row when the launcher reports its package changed.
 */
class CachedHomeAppResolverTest {
    private val delegate = CountingHomeAppResolver()
    private val rows = LauncherIconCache<CachedHomeAppRow>(16)
    private val resolver = CachedHomeAppResolver(delegate, rows)

    @Test
    fun repeatedResolutionsOfACandidateReuseTheResolvedRow() {
        val first = resolver.resolve(homeCandidate(score = 3.0))
        val second = resolver.resolve(homeCandidate(score = 3.0))

        assertEquals(1, delegate.resolutions)
        assertEquals(first, second)
        // The row is rebuilt with the candidate's score, so the resolved drawable is the evidence
        // that the launcher lookup did not run again.
        assertSame(first!!.icon, second!!.icon)
    }

    @Test
    fun aNewScoreDoesNotRepayTheResolution() {
        resolver.resolve(homeCandidate(score = 3.0))
        val rescored = resolver.resolve(homeCandidate(score = 9.0))

        assertEquals(1, delegate.resolutions)
        assertEquals(9.0, rescored!!.score, 0.0)
    }

    @Test
    fun aLauncherPackageChangeDropsTheCachedRow() {
        val before = resolver.resolve(homeCandidate(score = 3.0))
        resolver.invalidatePackage("first", profile = 0)
        val after = resolver.resolve(homeCandidate(score = 3.0))

        assertEquals(2, delegate.resolutions)
        assertNotSame(before!!.icon, after!!.icon)
    }

    @Test
    fun profilesAndPackagesKeepTheirOwnRows() {
        resolver.resolve(homeCandidate(score = 3.0))
        resolver.resolve(homeCandidate(packageName = "second", score = 2.0))
        resolver.resolve(homeCandidate(profile = 10, score = 1.0))

        assertEquals(3, delegate.resolutions)
    }

    @Test
    fun anUnresolvableCandidateStaysCachedAsUnresolvedUntilTheLauncherReportsAChange() {
        delegate.resolvable = false
        assertNull(resolver.resolve(homeCandidate(score = 3.0)))
        assertNull(resolver.resolve(homeCandidate(score = 3.0)))
        assertEquals(1, delegate.resolutions)

        delegate.resolvable = true
        resolver.invalidatePackage("first", profile = 0)
        assertEquals("first", resolver.resolve(homeCandidate(score = 3.0))?.packageName)
    }

    private fun homeCandidate(
        packageName: String = "first",
        profile: Int = 0,
        score: Double = 3.0,
    ) = HomeAppCandidate(packageName = packageName, user = profile, score = score)
}

private class CountingHomeAppResolver : HomeAppResolver {
    var resolutions = 0
    var resolvable = true

    override fun resolve(candidate: HomeAppCandidate): Application? {
        resolutions++
        if (!resolvable) return null
        return Application(
            label = candidate.packageName,
            packageName = candidate.packageName,
            // One instance per resolution, like the badged drawable the real resolver loads.
            icon = Any(),
            score = candidate.score,
        )
    }
}
