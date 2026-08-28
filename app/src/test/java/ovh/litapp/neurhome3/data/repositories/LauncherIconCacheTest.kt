package ovh.litapp.neurhome3.data.repositories

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LauncherIconCacheTest {
    private fun key(packageName: String, profile: Int = 0, component: String = "Main") =
        LauncherIconKey(packageName, profile, component)

    @Test
    fun reusesIconsForPackageProfileAndComponent() {
        val cache = LauncherIconCache<String>(2)
        var loads = 0

        val first = cache.getOrLoad(key("one")) { loads++; "icon" }
        val second = cache.getOrLoad(key("one")) { loads++; "new icon" }

        assertSame(first, second)
        assertEquals(1, loads)
    }

    @Test
    fun keepsComponentsAndProfilesIndependent() {
        val cache = LauncherIconCache<String>(4)
        var loads = 0

        cache.getOrLoad(key("one", component = "First")) { loads++; "first" }
        cache.getOrLoad(key("one", component = "Second")) { loads++; "second" }
        cache.getOrLoad(key("one", profile = 1)) { loads++; "profile" }

        assertEquals(3, loads)
    }

    @Test
    fun invalidatesAllComponentsForChangedPackageAndProfile() {
        val cache = LauncherIconCache<String>(4)
        var loads = 0
        val packageKey = key("one", component = "First")
        val otherProfileKey = key("one", profile = 1)

        cache.getOrLoad(packageKey) { loads++; "old" }
        cache.getOrLoad(otherProfileKey) { loads++; "other profile" }
        cache.invalidatePackage("one", 0)
        cache.getOrLoad(packageKey) { loads++; "new" }
        cache.getOrLoad(otherProfileKey) { loads++; "cached" }

        assertEquals(3, loads)
    }

    @Test
    fun evictsLeastRecentlyUsedEntry() {
        val cache = LauncherIconCache<String>(2)
        var loads = 0

        cache.getOrLoad(key("one")) { loads++; "one" }
        cache.getOrLoad(key("two")) { loads++; "two" }
        cache.getOrLoad(key("one")) { loads++; "one again" }
        cache.getOrLoad(key("three")) { loads++; "three" }
        cache.getOrLoad(key("two")) { loads++; "two again" }

        assertEquals(4, loads)
    }
}
