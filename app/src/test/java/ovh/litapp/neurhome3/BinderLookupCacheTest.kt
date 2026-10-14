package ovh.litapp.neurhome3

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The 30 s home refresh must not repeat the profile and quiet-mode binder calls, but a launcher
 * callback has to be able to make the next read pick up a changed profile set.
 */
class BinderLookupCacheTest {
    @Test
    fun readsOnceAndServesTheCachedValue() {
        var reads = 0
        val cache = BinderLookupCache { ++reads }

        assertEquals(1, cache.get())
        assertEquals(1, cache.get())
        assertEquals(1, cache.get())
        assertEquals(1, reads)
    }

    @Test
    fun invalidateForcesTheNextRead() {
        var reads = 0
        val cache = BinderLookupCache { ++reads }

        cache.get()
        cache.invalidate()

        assertEquals(2, cache.get())
        assertEquals(2, reads)
    }

    @Test
    fun invalidateWithoutAReadDoesNotRead() {
        var reads = 0
        val cache = BinderLookupCache { ++reads }

        cache.invalidate()

        assertEquals(0, reads)
        assertEquals(1, cache.get())
    }
}
