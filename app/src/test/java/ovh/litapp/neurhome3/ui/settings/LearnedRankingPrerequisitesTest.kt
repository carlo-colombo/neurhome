package ovh.litapp.neurhome3.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.litapp.neurhome3.data.repositories.HomeAppSelection

class LearnedRankingPrerequisitesTest {
    private val satisfied = LearnedRankingPrerequisites(
        wifiLogging = true,
        positionLogging = true,
        locationPermissionGranted = true
    )

    @Test
    fun allRequirementsAreNeeded() {
        assertTrue(satisfied.isSatisfied)
        assertFalse(satisfied.copy(wifiLogging = false).isSatisfied)
        assertFalse(satisfied.copy(positionLogging = false).isSatisfied)
        assertFalse(satisfied.copy(locationPermissionGranted = false).isSatisfied)
    }

    @Test
    fun missingRequirementsAreExplainedIndividually() {
        assertEquals(
            listOf("Wi-Fi logging is off"),
            satisfied.copy(wifiLogging = false).missingRequirements()
        )
        assertEquals(
            listOf("position logging is off", "location permission is not granted"),
            LearnedRankingPrerequisites(
                wifiLogging = true,
                positionLogging = false,
                locationPermissionGranted = false
            ).missingRequirements()
        )
    }

    @Test
    fun noExplanationWhenRequirementsAreMet() {
        assertNull(satisfied.explain(HomeAppSelection.CLASSIC))
        assertNull(satisfied.explain(HomeAppSelection.LEARNED))
    }

    @Test
    fun selectedLearnedRankingReportsThatHomeFallsBack() {
        val message = satisfied.copy(positionLogging = false)
            .explain(HomeAppSelection.LEARNED)

        assertTrue(message!!.contains("Learned ranking is inactive"))
        assertTrue(message.contains("position logging is off"))
        assertTrue(message.contains("classic ranking"))
    }

    @Test
    fun unselectedLearnedRankingExplainsWhatIsRequired() {
        val message = satisfied.copy(wifiLogging = false, locationPermissionGranted = false)
            .explain(HomeAppSelection.CLASSIC)

        assertTrue(message!!.contains("Wi-Fi logging is off"))
        assertTrue(message.contains("location permission is not granted"))
        assertFalse(message.contains("inactive"))
    }

    @Test
    fun revokedRequirementsNeverReselectClassic() {
        // The preference is owned by the repository; prerequisites only report
        // activity, so a revoked logger keeps the stored learned selection.
        val prerequisites = satisfied.copy(
            wifiLogging = false,
            positionLogging = false,
            locationPermissionGranted = false
        )

        assertEquals(3, prerequisites.missingRequirements().size)
        assertTrue(prerequisites.explain(HomeAppSelection.LEARNED)!!.contains("classic ranking"))
    }
}
