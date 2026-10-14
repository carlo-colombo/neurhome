package ovh.litapp.neurhome3.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchLogQueryTest {
    @Test
    fun explicitQueryIsLoggedInsteadOfHomeQuery() {
        assertEquals(
            "maps",
            launchLogQuery(explicitQuery = "maps", homeQuery = listOf("c", "a", "l"))
        )
    }

    @Test
    fun homeQueryIsLoggedWhenNoExplicitQueryIsGiven() {
        assertEquals(
            "cal",
            launchLogQuery(explicitQuery = null, homeQuery = listOf("c", "a", "l"))
        )
    }

    @Test
    fun emptyHomeQueryLogsEmptyString() {
        assertEquals(
            "",
            launchLogQuery(explicitQuery = null, homeQuery = emptyList())
        )
    }
}
