package ovh.litapp.neurhome3.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.litapp.neurhome3.ComposeTestActivity
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.ui.INeurhomeViewModel

/**
 * Pins the tag write guard in [ApplicationItem]: opening the row, editing the tag set and closing it
 * must persist whenever the edited set differs from the last persisted set, including when the user
 * reverts to the set the row first composed with.
 */
@OptIn(ExperimentalPermissionsApi::class)
@RunWith(AndroidJUnit4::class)
class ApplicationItemTagsTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    private val app = Application(
        label = "Test app",
        packageName = "test.app",
        icon = null,
        tags = listOf("A"),
    )
    private val tagWrites = mutableListOf<Set<String>>()
    private val tagActions = INeurhomeViewModel.AppTagActions(
        setTags = { _, tags -> tagWrites += tags }
    )

    @Test
    fun revertingToTheOriginalTagSetStillPersists() {
        composeTestRule.setContent {
            Column {
                ApplicationItem(
                    app = app,
                    onLaunch = { _, _ -> },
                    tagActions = tagActions,
                    permission = TagsTestGrantedPermission,
                    manageEntry = true,
                    availableTags = listOf("A", "B"),
                )
            }
        }

        toggleManagementPanel()
        composeTestRule.onNodeWithText("B").performClick()
        toggleManagementPanel()

        assertEquals("adding a tag must persist on collapse", listOf(setOf("A", "B")), tagWrites)

        toggleManagementPanel()
        composeTestRule.onNodeWithText("B").performClick()
        toggleManagementPanel()

        assertEquals(
            "removing the added tag must persist the reverted set, not skip against the initial set",
            listOf(setOf("A", "B"), setOf("A")),
            tagWrites,
        )
    }

    @Test
    fun closingWithoutEditingDoesNotWrite() {
        composeTestRule.setContent {
            Column {
                ApplicationItem(
                    app = app,
                    onLaunch = { _, _ -> },
                    tagActions = tagActions,
                    permission = TagsTestGrantedPermission,
                    manageEntry = true,
                    availableTags = listOf("A", "B"),
                )
            }
        }

        toggleManagementPanel()
        toggleManagementPanel()

        assertEquals(emptyList<Set<String>>(), tagWrites)
    }

    private fun toggleManagementPanel() {
        composeTestRule.onNodeWithText(app.label).performTouchInput { longClick() }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
private object TagsTestGrantedPermission : PermissionState {
    override val permission: String = "ovh.litapp.neurhome3.test.PERMISSION"
    override val status: PermissionStatus = PermissionStatus.Granted
    override fun launchPermissionRequest() = Unit
}
