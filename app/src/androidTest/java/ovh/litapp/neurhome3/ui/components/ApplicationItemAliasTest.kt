package ovh.litapp.neurhome3.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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

@OptIn(ExperimentalPermissionsApi::class)
@RunWith(AndroidJUnit4::class)
class ApplicationItemAliasTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    private val app = Application(label = "Test app", packageName = "test.app", icon = null)
    private val otherApp = Application(label = "Other app", packageName = "other.app", icon = null)
    private val aliasWrites = mutableListOf<String>()
    private val appActions = INeurhomeViewModel.AppActions(
        setAlias = { _, alias -> aliasWrites += alias }
    )

    @Test
    fun typingAnAliasDoesNotWriteUntilTheRowCloses() {
        renderApplicationItems()
        toggleManagementPanel(app)

        composeTestRule.onNode(hasSetTextAction())
            .performClick()
            .performTextInput("custom alias")

        composeTestRule.onNode(hasSetTextAction()).assertTextContains("custom alias")
        assertEquals("typing must not persist the alias per keystroke", emptyList<String>(), aliasWrites)

        toggleManagementPanel(app)

        assertEquals(listOf("custom alias"), aliasWrites)
    }

    @Test
    fun tappingAnotherAliasFieldPersistsTheEditedAlias() {
        renderApplicationItems(listOf(app, otherApp))
        toggleManagementPanel(app)
        toggleManagementPanel(otherApp)

        val aliasFields = composeTestRule.onAllNodes(hasSetTextAction())
        aliasFields[0].performTouchInput { click() }
        aliasFields[0].assertIsFocused()
        aliasFields[0].performTextInput("custom alias")

        assertEquals("typing must not persist the alias per keystroke", emptyList<String>(), aliasWrites)

        aliasFields[1].performTouchInput { click() }
        aliasFields[0].assertIsNotFocused()

        assertEquals(
            "losing focus must persist the alias while the row stays open",
            listOf("custom alias"),
            aliasWrites,
        )
        // The row is still open, so closing it must not write the alias a second time.
        toggleManagementPanel(app)
        assertEquals(listOf("custom alias"), aliasWrites)
    }

    @Test
    fun clearingTheAliasPersistsTheEmptyValueWhenEditingEnds() {
        val existingAlias = app.copy(alias = "existing")
        renderApplicationItems(listOf(existingAlias))
        toggleManagementPanel(existingAlias)

        composeTestRule.onNode(hasSetTextAction()).performTextClearance()

        assertEquals("clearing must not write before editing ends", emptyList<String>(), aliasWrites)

        toggleManagementPanel(existingAlias)

        assertEquals(listOf(""), aliasWrites)
    }

    @Test
    fun closingWithoutEditingDoesNotWrite() {
        renderApplicationItems()
        toggleManagementPanel(app)

        toggleManagementPanel(app)

        assertEquals(emptyList<String>(), aliasWrites)
    }

    private fun renderApplicationItems(apps: List<Application> = listOf(app)) {
        composeTestRule.setContent {
            Column {
                apps.forEach { item ->
                    ApplicationItem(
                        app = item,
                        appActions = appActions,
                        permission = GrantedPermission,
                        manageEntry = true,
                    )
                }
            }
        }
    }

    private fun toggleManagementPanel(target: Application = app) {
        composeTestRule.onNodeWithText(target.label).performTouchInput { longClick() }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
private object GrantedPermission : PermissionState {
    override val permission: String = "ovh.litapp.neurhome3.test.PERMISSION"
    override val status: PermissionStatus = PermissionStatus.Granted
    override fun launchPermissionRequest() = Unit
}
