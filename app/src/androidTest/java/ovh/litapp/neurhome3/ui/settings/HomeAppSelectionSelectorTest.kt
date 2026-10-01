package ovh.litapp.neurhome3.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.litapp.neurhome3.ComposeTestActivity
import ovh.litapp.neurhome3.data.repositories.HomeAppSelection

@RunWith(AndroidJUnit4::class)
class HomeAppSelectionSelectorTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    private val satisfied = LearnedRankingPrerequisites(
        wifiLogging = true,
        positionLogging = true,
        locationPermissionGranted = true
    )

    @Test
    fun classicIsSelectedByDefault() {
        render(selection = HomeAppSelection.CLASSIC, prerequisites = satisfied)

        composeTestRule.onNodeWithTag(HomeAppSelectionClassicTestTag).assertIsSelected()
        composeTestRule.onNodeWithTag(HomeAppSelectionLearnedTestTag).assertIsNotSelected()
    }

    @Test
    fun learnedSelectionIsRestoredFromThePersistedSetting() {
        render(selection = HomeAppSelection.LEARNED, prerequisites = satisfied)

        composeTestRule.onNodeWithTag(HomeAppSelectionLearnedTestTag).assertIsSelected()
        composeTestRule.onNodeWithTag(HomeAppSelectionClassicTestTag).assertIsNotSelected()
    }

    @Test
    fun selectingLearnedReportsTheChoiceAndRequestsLocationPermission() {
        var requested = false
        var selected = HomeAppSelection.CLASSIC
        render(
            selection = HomeAppSelection.CLASSIC,
            prerequisites = satisfied.copy(locationPermissionGranted = false),
            onRequestLocationPermission = { requested = true },
            onSelect = { selected = it }
        )

        composeTestRule.onNodeWithTag(HomeAppSelectionLearnedTestTag).performClick()

        assertEquals(HomeAppSelection.LEARNED, selected)
        assertTrue("the location permission must be requested on explicit selection", requested)
        composeTestRule.onNodeWithTag(HomeAppSelectionLearnedTestTag).assertIsSelected()
    }

    @Test
    fun selectingLearnedWithThePermissionGrantedDoesNotRequestItAgain() {
        var requested = false
        render(
            selection = HomeAppSelection.CLASSIC,
            prerequisites = satisfied,
            onRequestLocationPermission = { requested = true },
            onSelect = {}
        )

        composeTestRule.onNodeWithTag(HomeAppSelectionLearnedTestTag).performClick()

        assertFalse(requested)
    }

    @Test
    fun switchingBackToClassicNeverRequestsThePermission() {
        var requested = false
        var selected = HomeAppSelection.LEARNED
        render(
            selection = HomeAppSelection.LEARNED,
            prerequisites = satisfied.copy(locationPermissionGranted = false),
            onRequestLocationPermission = { requested = true },
            onSelect = { selected = it }
        )

        composeTestRule.onNodeWithTag(HomeAppSelectionClassicTestTag).performClick()

        assertEquals(HomeAppSelection.CLASSIC, selected)
        assertFalse(requested)
    }

    @Test
    fun unmetPrerequisitesAreExplainedWhileClassicIsSelected() {
        render(
            selection = HomeAppSelection.CLASSIC,
            prerequisites = LearnedRankingPrerequisites(
                wifiLogging = false,
                positionLogging = false,
                locationPermissionGranted = false
            )
        )

        composeTestRule.onNodeWithTag(HomeAppSelectionPrerequisiteTestTag).assertIsDisplayed()
            .assertTextContains("Wi-Fi logging is off", substring = true)
            .assertTextContains("position logging is off", substring = true)
            .assertTextContains("location permission is not granted", substring = true)
    }

    @Test
    fun unmetPrerequisitesExplainThatHomeFallsBackToClassic() {
        render(
            selection = HomeAppSelection.LEARNED,
            prerequisites = satisfied.copy(positionLogging = false)
        )

        val text = composeTestRule
            .onNodeWithTag(HomeAppSelectionPrerequisiteTestTag)
            .fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text]
            .joinToString { it.text }
        assertTrue(text.contains("Learned ranking is inactive"))
        assertTrue(text.contains("classic ranking"))
    }

    @Test
    fun noExplanationWhenEveryPrerequisiteIsMet() {
        render(selection = HomeAppSelection.LEARNED, prerequisites = satisfied)

        composeTestRule.onNodeWithTag(HomeAppSelectionSelectorTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeAppSelectionPrerequisiteTestTag).assertDoesNotExist()
    }

    private fun render(
        selection: HomeAppSelection,
        prerequisites: LearnedRankingPrerequisites,
        onRequestLocationPermission: () -> Unit = {},
        onSelect: (HomeAppSelection) -> Unit = {}
    ) {
        composeTestRule.setContent {
            var current by remember { mutableStateOf(selection) }
            HomeAppSelectionSelector(
                selection = current,
                prerequisites = prerequisites,
                onRequestLocationPermission = onRequestLocationPermission,
                onSelect = {
                    current = it
                    onSelect(it)
                }
            )
        }
    }
}
