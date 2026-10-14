package ovh.litapp.neurhome3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {
    @Test
    fun destinationsHaveUniqueRoutes() {
        val routes = Navigator.destinations.map { it.route }

        assertEquals(routes.size, routes.toSet().size)
        assertTrue(routes.contains(Navigator.Destination.Home.route))
    }

    @Test
    fun destinationsRegisterEverySealedDestinationExactlyOnce() {
        // A new Destination that is not added to `Navigator.destinations` would never be
        // reachable from the NavHost, so derive the expected route set from the sealed hierarchy.
        val declaredRoutes = Navigator.Destination::class.java.declaredClasses
            .filter { Navigator.Destination::class.java.isAssignableFrom(it) }
            .map { it.getDeclaredField("INSTANCE").get(null) as Navigator.Destination }
            .map { it.route }
            .toSet()

        assertEquals(
            declaredRoutes,
            Navigator.destinations.map { it.route }.toSet()
        )
    }
}
