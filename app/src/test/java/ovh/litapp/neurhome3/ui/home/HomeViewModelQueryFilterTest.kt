package ovh.litapp.neurhome3.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.ApplicationVisibility

/**
 * Pins the home search filtering behind [filterApplicationsForQuery]: an empty key list yields no
 * suggestions, otherwise the label/alias match, the hidden-from-filter exclusion, the six-app cap
 * and the display order are what the Home filter renders.
 */
class HomeViewModelQueryFilterTest {
    @Test
    fun emptyQueryYieldsNoSuggestions() {
        assertEquals(
            emptyList<Application>(),
            filterApplicationsForQuery(listOf(app("Calendar")), emptyList())
        )
    }

    @Test
    fun matchesLabelAndAliasCaseInsensitively() {
        val apps = listOf(
            app("Calendar", score = 1.0),
            app("Camera", alias = "myCal", score = 2.0),
            app("Clock", score = 3.0),
        )

        assertEquals(
            listOf("Calendar", "Camera"),
            filterApplicationsForQuery(apps, listOf("c", "a", "l")).map { it.label }
        )
    }

    @Test
    fun optionalMyPrefixIsPartOfTheMatch() {
        val apps = listOf(app("myMap"), app("Map"), app("Compass"))

        assertEquals(
            listOf("Map", "myMap"),
            filterApplicationsForQuery(apps, listOf("m", "a", "p")).map { it.label }
        )
    }

    @Test
    fun hiddenFromFilteredAppsAreExcludedEvenWhenTheyMatch() {
        val apps = listOf(
            app("Calendar", score = 9.0, visibility = ApplicationVisibility.HIDDEN_FROM_FILTERED),
            app("Camera", score = 1.0),
        )

        assertEquals(
            listOf("Camera"),
            filterApplicationsForQuery(apps, listOf("c", "a")).map { it.label }
        )
    }

    @Test
    fun keepsTheSixBestScoresInDisplayOrder() {
        val apps = (1..8).map { app("app$it", score = it.toDouble()) }

        // The six best scores (8..3) are kept, then reversed into display order: 3,4,5,6,7,8.
        assertEquals(
            listOf("app3", "app4", "app5", "app6", "app7", "app8"),
            filterApplicationsForQuery(apps, listOf("a", "p", "p")).map { it.label }
        )
    }

    @Test
    fun unmatchedQueryYieldsNoSuggestions() {
        assertEquals(
            emptyList<Application>(),
            filterApplicationsForQuery(listOf(app("Calendar")), listOf("z", "z"))
        )
    }

    private fun app(
        label: String,
        alias: String = "",
        score: Double = 0.0,
        visibility: ApplicationVisibility = ApplicationVisibility.VISIBLE,
    ) = Application(
        label = label,
        packageName = label,
        icon = null,
        score = score,
        visibility = visibility,
        alias = alias,
    )
}
