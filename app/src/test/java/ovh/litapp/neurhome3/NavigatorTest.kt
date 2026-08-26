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
}
