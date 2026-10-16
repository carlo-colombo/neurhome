package ovh.litapp.neurhome3.ui.home

import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
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
 * panel opening there and the tag write once the row collapses.
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

        longPress()

        composeTestRule.onNode(hasSetTextAction()).assertExists()
        composeTestRule.onNodeWithText("Work").assertExists()
    }

    @Test
    fun longPressOpensTheConfigurationPanelWhileFiltering() {
        render(filtering = true)

        composeTestRule.onNode(hasSetTextAction()).assertDoesNotExist()

        longPress()

        composeTestRule.onNode(hasSetTextAction()).assertExists()
        composeTestRule.onNodeWithText("Work").assertExists()
    }

    @Test
    fun selectingATagWhileFilteringPersistsOnCollapse() {
        render(filtering = true)

        longPress()
        composeTestRule.onNodeWithText("Work").performTouchInput { click() }
        longPress()

        assertEquals(listOf(setOf("Work")), tagWrites)
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

    private fun longPress() {
        composeTestRule.onNodeWithText(app.label).performTouchInput { longClick() }
    }
}
