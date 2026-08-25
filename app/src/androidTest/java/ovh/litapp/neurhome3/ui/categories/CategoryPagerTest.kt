package ovh.litapp.neurhome3.ui.categories

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import ovh.litapp.neurhome3.ComposeTestActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

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
        composeTestRule.onNodeWithTag(CategoryPagerTestTag)
            .performTouchInput { swipeLeft() }
        composeTestRule.onNodeWithTag(UncategorizedScreenTestTag).assertIsDisplayed()
    }

    @Test
    fun uncategorizedIsTheFinalPageWhenThereAreNoTags() {
        composeTestRule.setContent {
            CategoryPager { _ ->
                androidx.compose.material3.Text("Home")
            }
        }
        composeTestRule.onNodeWithTag(CategoryPagerTestTag)
            .performTouchInput { swipeLeft() }
        composeTestRule.onNodeWithTag(UncategorizedScreenTestTag).assertIsDisplayed()
    }
}
