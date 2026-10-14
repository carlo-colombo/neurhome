package ovh.litapp.neurhome3.ui.components

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    private val aliasWrites = mutableListOf<String>()
    private val appActions = INeurhomeViewModel.AppActions(
        setAlias = { _, alias -> aliasWrites += alias }
    )

    @Test
    fun typingAnAliasDoesNotWriteUntilTheRowCloses() {
        renderApplicationItem()
        toggleManagementPanel()

        composeTestRule.onNode(hasSetTextAction())
            .performClick()
            .performTextInput("custom alias")

        composeTestRule.onNode(hasSetTextAction()).assertTextContains("custom alias")
        assertEquals("typing must not persist the alias per keystroke", emptyList<String>(), aliasWrites)

        toggleManagementPanel()

        assertEquals(listOf("custom alias"), aliasWrites)
    }

    @Test
    fun closingWithoutEditingDoesNotWrite() {
        renderApplicationItem()
        toggleManagementPanel()

        toggleManagementPanel()

        assertEquals(emptyList<String>(), aliasWrites)
    }

    private fun renderApplicationItem() {
        composeTestRule.setContent {
            ApplicationItem(
                app = app,
                appActions = appActions,
                permission = GrantedPermission,
                manageEntry = true,
            )
        }
    }

    private fun toggleManagementPanel() {
        composeTestRule.onNodeWithText(app.label).performTouchInput { longClick() }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
private object GrantedPermission : PermissionState {
    override val permission: String = "ovh.litapp.neurhome3.test.PERMISSION"
    override val status: PermissionStatus = PermissionStatus.Granted
    override fun launchPermissionRequest() = Unit
}
