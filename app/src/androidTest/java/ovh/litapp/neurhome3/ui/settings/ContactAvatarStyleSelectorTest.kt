package ovh.litapp.neurhome3.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.litapp.neurhome3.ComposeTestActivity
import ovh.litapp.neurhome3.data.avatar.DiceBearStyle

/**
 * Drives the stateless avatar-style selector. The previews are a `LazyRow`, so a style is reachable
 * only after the row scrolls to it - which is exactly the CI 320dp/mdpi constraint: a few previews
 * fit, the rest must scroll into view rather than be composed all at once.
 */
@RunWith(AndroidJUnit4::class)
class ContactAvatarStyleSelectorTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun everyStyleIsOfferedWithItsOwnPreview() {
        render(selection = DiceBearStyle.SHADOWS)

        composeTestRule.onNodeWithTag(ContactAvatarStyleSelectorTestTag).assertIsDisplayed()
        DiceBearStyle.entries.forEach { style ->
            scrollTo(style)
            composeTestRule.onNodeWithTag(contactAvatarStyleOptionTestTag(style)).assertIsDisplayed()
        }
    }

    @Test
    fun thePersistedStyleIsTheSelectedOne() {
        render(selection = DiceBearStyle.SHADOWS)

        composeTestRule.onNodeWithTag(contactAvatarStyleOptionTestTag(DiceBearStyle.IDENTICON))
            .assertIsNotSelected()
        scrollTo(DiceBearStyle.SHADOWS)
        composeTestRule.onNodeWithTag(contactAvatarStyleOptionTestTag(DiceBearStyle.SHADOWS))
            .assertIsSelected()
    }

    @Test
    fun choosingAStyleReportsItAndSelectsIt() {
        var selected = DiceBearStyle.SHADOWS
        render(selection = DiceBearStyle.SHADOWS, onSelect = { selected = it })

        scrollTo(DiceBearStyle.RINGS)
        composeTestRule.onNodeWithTag(contactAvatarStyleOptionTestTag(DiceBearStyle.RINGS))
            .performClick()

        assertEquals(DiceBearStyle.RINGS, selected)
        composeTestRule.onNodeWithTag(contactAvatarStyleOptionTestTag(DiceBearStyle.RINGS))
            .assertIsSelected()
    }

    @Test
    fun noneIsOfferedAndSelectable() {
        var selected = DiceBearStyle.SHADOWS
        render(selection = DiceBearStyle.SHADOWS, onSelect = { selected = it })

        scrollTo(DiceBearStyle.NONE)
        composeTestRule.onNodeWithTag(contactAvatarStyleOptionTestTag(DiceBearStyle.NONE))
            .performClick()

        assertEquals(DiceBearStyle.NONE, selected)
        composeTestRule.onNodeWithTag(contactAvatarStyleOptionTestTag(DiceBearStyle.NONE))
            .assertIsSelected()
    }

    @Test
    fun creditsStayOutOfTheWayUntilAskedFor() {
        render(selection = DiceBearStyle.SHADOWS)

        composeTestRule.onNodeWithTag(ContactAvatarStyleCreditsTestTag).assertDoesNotExist()
        composeTestRule.onAllNodesWithText("CC0 1.0", substring = true).assertCountEquals(0)

        composeTestRule.onNodeWithTag(ContactAvatarStyleCreditsToggleTestTag).performClick()

        // Rendered from the vendored definitions' meta.license.text, not from a literal in the UI.
        composeTestRule.onNodeWithTag(ContactAvatarStyleCreditsTestTag).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("CC0 1.0", substring = true).onFirst().assertIsDisplayed()
    }

    private fun scrollTo(style: DiceBearStyle) {
        composeTestRule.onNodeWithTag(ContactAvatarStyleRowTestTag)
            .performScrollToNode(hasTestTag(contactAvatarStyleOptionTestTag(style)))
    }

    private fun render(
        selection: DiceBearStyle,
        onSelect: (DiceBearStyle) -> Unit = {},
    ) {
        composeTestRule.setContent {
            var current by remember { mutableStateOf(selection) }
            ContactAvatarStyleSelector(
                selection = current,
                onSelect = {
                    current = it
                    onSelect(it)
                }
            )
        }
    }
}
