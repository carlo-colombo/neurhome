package ovh.litapp.neurhome3.ui.components

import android.content.Intent
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.litapp.neurhome3.ComposeTestActivity
import ovh.litapp.neurhome3.data.models.Event
import ovh.litapp.neurhome3.ui.INeurhomeViewModel
import ovh.litapp.neurhome3.ui.home.FilteredUIState
import ovh.litapp.neurhome3.ui.home.IHomeViewModel
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme

@RunWith(AndroidJUnit4::class)
class KeyboardA11yTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    private val viewModel = FakeHomeViewModel()

    @Test
    fun keysAnnounceTheirT9DigitAndLetters() {
        renderKeyboard()

        composeTestRule.onNodeWithContentDescription("3, d, e, f").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Digits 0 to 9").assertIsDisplayed()
        // The accessible label replaces the visible glyph instead of being announced twice.
        composeTestRule.onNodeWithText("def").assertDoesNotExist()
    }

    @Test
    fun keyboardIsATraversalGroup() {
        renderKeyboard()

        composeTestRule.onNodeWithTag(KeyboardTestTag)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true))
    }

    @Test
    fun tappingAKeyPushesItsT9Token() {
        renderKeyboard()

        composeTestRule.onNodeWithTag(keyboardKeyTestTag("def")).performClick()

        assertEquals(listOf("[def]"), viewModel.pushed)
    }

    @Test
    fun queryRowIsAPoliteLiveRegionWithReadableText() {
        renderKeyboard(query = listOf("[abc]", "[def]"))

        composeTestRule.onNodeWithTag(KeyboardQueryTestTag)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        composeTestRule.onNodeWithContentDescription("abc def").assertIsDisplayed()
    }

    @Test
    fun backspaceAndClearAreLabeledAndClickable() {
        renderKeyboard(query = listOf("[abc]"))

        composeTestRule.onNodeWithTag(KeyboardBackspaceTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(KeyboardClearTestTag).performClick()

        assertTrue(viewModel.cleared)
    }

    private fun renderKeyboard(query: List<String> = emptyList()) {
        composeTestRule.setContent {
            Neurhome3Theme(dynamicColor = false) {
                Keyboard(viewModel, FilteredUIState(query = query))
            }
        }
    }
}

private class FakeHomeViewModel : IHomeViewModel {
    val pushed = mutableListOf<String>()
    var cleared = false
    var popped = false

    override fun push(s: String) {
        pushed += s
    }

    override fun clearQuery() {
        cleared = true
    }

    override fun pop() {
        popped = true
    }

    override fun openAlarms() = Unit
    override fun fetchWeather() = Unit
    override val vibrate: () -> Unit = {}
    override fun openCalendar(event: Event) = Unit
    override val getBattery: () -> Intent? = { null }
    override val appActions = INeurhomeViewModel.AppActions()
}
