package ovh.litapp.neurhome3.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.litapp.neurhome3.ComposeTestActivity
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.ui.INeurhomeViewModel

/**
 * The Home screen long press must open the same per-application configuration panel as the full
 * applications screen and the category screens. Both Home layouts (the top-apps list and the list
 * shown while the T9 filter is active) render through [HomeApplicationsList], so this pins the
 * panel opening there, the tag write once the row collapses, and the scroll container that keeps a
 * panel taller than the Home slot reachable instead of drawing over the keyboard.
 */
@RunWith(AndroidJUnit4::class)
class HomeApplicationsListTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    private val app = Application(
        label = "Test app",
        packageName = "test.app",
        icon = null,
    )
    private val tagWrites = mutableListOf<Set<String>>()
    private val tagActions = INeurhomeViewModel.AppTagActions(
        setTags = { _, tags -> tagWrites += tags },
    )

    @Test
    fun longPressOpensTheConfigurationPanelInTheTopAppsList() {
        render(filtering = false)

        composeTestRule.onNode(hasSetTextAction()).assertDoesNotExist()

        longPress(app.label)

        composeTestRule.onNode(hasSetTextAction()).assertExists()
        composeTestRule.onNodeWithText("Work").assertExists()
    }

    @Test
    fun longPressOpensTheConfigurationPanelWhileFiltering() {
        render(filtering = true)

        composeTestRule.onNode(hasSetTextAction()).assertDoesNotExist()

        longPress(app.label)

        composeTestRule.onNode(hasSetTextAction()).assertExists()
        composeTestRule.onNodeWithText("Work").assertExists()
    }

    @Test
    fun selectingATagWhileFilteringPersistsOnCollapse() {
        render(filtering = true)

        longPress(app.label)
        composeTestRule.onNodeWithText("Work").performTouchInput { click() }
        longPress(app.label)

        assertEquals(listOf(setOf("Work")), tagWrites)
    }

    @Test
    fun anExpandedPanelTallerThanTheSlotIsScrollable() {
        val apps = (1..3).map { Application("App $it", "test.app.$it", icon = null) }
        composeTestRule.setContent {
            // Simulates the weight-bounded Home slot: shorter than three collapsed rows plus a
            // panel, so the container needs a scroll range once a row opens.
            Box(Modifier.height(220.dp)) {
                HomeApplicationsList(
                    list = apps,
                    onLaunch = { _, _ -> },
                    filtering = false,
                    managementActions = INeurhomeViewModel.AppManagementActions(),
                    tagActions = tagActions,
                    availableTags = listOf("Work"),
                )
            }
        }

        longPress("App 1")

        // Without a scrollable container this would fail: the panel's last control sits below the
        // 220dp slot and would be clipped.
        composeTestRule.onNodeWithContentDescription("Uninstall").performScrollTo().assertIsDisplayed()
    }

    private fun render(filtering: Boolean) {
        composeTestRule.setContent {
            HomeApplicationsList(
                list = listOf(app),
                onLaunch = { _, _ -> },
                filtering = filtering,
                managementActions = INeurhomeViewModel.AppManagementActions(),
                tagActions = tagActions,
                availableTags = listOf("Work"),
            )
        }
    }

    private fun longPress(label: String) {
        composeTestRule.onNodeWithText(label).performTouchInput { longClick() }
    }
}
