package ovh.litapp.neurhome3.ui.components

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.litapp.neurhome3.ComposeTestActivity
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class WatchA11yTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun clockIsAnAnnouncedButtonThatOpensAlarms() {
        var opened = false
        composeTestRule.setContent {
            Neurhome3Theme(dynamicColor = false) {
                Watch(openAlarms = { opened = true }, now = ZonedDateTime.now())
            }
        }

        val watch = composeTestRule.onNode(hasClickAction())
            .assert(hasOnClickLabel("Open alarms"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        watch.performClick()

        assertTrue("clicking the clock must open alarms", opened)
    }
}

private fun hasOnClickLabel(label: String): SemanticsMatcher =
    SemanticsMatcher("has onClick label '$label'") { node ->
        node.config[SemanticsActions.OnClick]?.label == label
    }
