package ovh.litapp.neurhome3.ui.categories

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.litapp.neurhome3.ComposeTestActivity

/**
 * How many pages the pager may hold before a swipe-to-the-end helper gives up. The pager renders
 * one page per persisted tag between Home and Uncategorized, so the bound has to leave room for
 * the tags a device already has.
 */
private const val MaxPagerSwipes = 16

/** How long the pager may take to settle on Uncategorized after a swipe. */
private const val PagerSettleTimeoutMillis = 3_000L

@RunWith(AndroidJUnit4::class)
class CategoryPagerTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun swipingLeftFromHomeReachesUncategorized() {
        composeTestRule.setContent {
            CategoryPager { _ ->
                androidx.compose.material3.Text("Home")
            }
        }
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        swipeToFinalPage()
        composeTestRule.onNodeWithTag(UncategorizedScreenTestTag).assertIsDisplayed()
    }

    @Test
    fun uncategorizedIsTheFinalPageWhenThereAreNoTags() {
        composeTestRule.setContent {
            CategoryPager { _ ->
                androidx.compose.material3.Text("Home")
            }
        }
        swipeToFinalPage()
        composeTestRule.onNodeWithTag(UncategorizedScreenTestTag).assertIsDisplayed()
        // Already on the last page: an extra swipe must not move past it.
        composeTestRule.onNodeWithTag(CategoryPagerTestTag)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(UncategorizedScreenTestTag).assertIsDisplayed()
    }

    /**
     * Swipes towards Uncategorized until that page is actually composed instead of swiping a fixed
     * number of times, so the result does not depend on how fast the pager settles. The final
     * `waitUntil` both waits for a late settle and reports the swipe count when the page never
     * appears.
     */
    private fun swipeToFinalPage() {
        repeat(MaxPagerSwipes) {
            if (uncategorizedPageIsOnScreen()) return
            composeTestRule.onNodeWithTag(CategoryPagerTestTag)
                .performTouchInput { swipeLeft() }
            composeTestRule.waitForIdle()
        }
        composeTestRule.waitUntil(
            "Uncategorized was not displayed after $MaxPagerSwipes swipes; " +
                "the pager did not settle on its final page.",
            PagerSettleTimeoutMillis,
        ) { uncategorizedPageIsOnScreen() }
    }

    private fun uncategorizedPageIsOnScreen(): Boolean =
        composeTestRule.onAllNodesWithTag(UncategorizedScreenTestTag)
            .fetchSemanticsNodes()
            .isNotEmpty()
}
