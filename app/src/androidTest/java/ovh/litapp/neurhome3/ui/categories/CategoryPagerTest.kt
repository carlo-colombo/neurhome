package ovh.litapp.neurhome3.ui.categories

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.litapp.neurhome3.ComposeTestActivity
import ovh.litapp.neurhome3.application.NeurhomeApplication

/**
 * How many pages the pager may hold before a swipe helper gives up. The pager renders one page per
 * persisted tag between Home and Uncategorized, so the bound has to leave room for the tags a
 * device already has.
 */
private const val MaxPagerSwipes = 24

/** How long the pager may take to settle on a page, or a write to land and be visible. */
private const val SettleTimeoutMillis = 5_000L

/** Which of the two shared-pager entry paths a test drives. */
private enum class CategoryEntryPath {
    /** The Home destination: `CategoryPager(includeHome = true, ...)`. */
    Home,

    /** The dedicated Categories destination, which is the same pager without the Home page. */
    Categories,
}

/**
 * Pins the shared pager behaviour on both entry paths. Both are the same `CategoryPager`, so the
 * move-left/move-right controls, the removal confirmation and the Uncategorized tag-name errors
 * have to behave identically whether the pager was opened from Home or from the dedicated
 * Categories destination; a change that only fixes one path leaves the other broken.
 *
 * The tags used here are created against the application's own `TagRepository` - the same
 * repository `AppViewModelFactory` hands the screen's ViewModel - under unique names, and removed
 * again afterwards, so the assertions do not depend on what the device already has. Only the tests
 * that walk to a tag page create them: the two tests that assert what the pager does without tags
 * keep the list empty on purpose. Waits are bounded `waitUntil` conditions on the observable
 * result (the persisted order, the dialog, the error text), never a sleep.
 */
@RunWith(AndroidJUnit4::class)
class CategoryPagerTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    private val application: NeurhomeApplication
        get() = ApplicationProvider.getApplicationContext()

    private val stamp = System.nanoTime()

    /** Created as the second-to-last and last tags, so [lastTag] cannot move right. */
    private val firstTag = "A-parity-$stamp"
    private val lastTag = "B-parity-$stamp"

    @After
    fun removeTags() = runBlocking {
        application.tagRepository.tags.first()
            .map { it.name }
            .filter { it == firstTag || it == lastTag }
            .forEach { application.tagRepository.deleteTag(it) }
    }

    @Test
    fun swipingLeftFromHomeReachesUncategorized() {
        showPager(CategoryEntryPath.Home)
        composeTestRule.onNodeWithText("Home dashboard").assertIsDisplayed()
        swipeToFinalPage()
        composeTestRule.onNodeWithTag(UncategorizedScreenTestTag).assertIsDisplayed()
    }

    @Test
    fun uncategorizedIsTheFinalPageWhenThereAreNoTags() {
        showPager(CategoryEntryPath.Home)
        swipeToFinalPage()
        composeTestRule.onNodeWithTag(UncategorizedScreenTestTag).assertIsDisplayed()
        // Already on the last page: an extra swipe must not move past it.
        composeTestRule.onNodeWithTag(CategoryPagerTestTag)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(UncategorizedScreenTestTag).assertIsDisplayed()
    }

    @Test
    fun movingATagLeftReordersThePagesOnTheHomePath() =
        assertMovingATagLeftReorders(CategoryEntryPath.Home)

    @Test
    fun movingATagLeftReordersThePagesOnTheCategoriesPath() =
        assertMovingATagLeftReorders(CategoryEntryPath.Categories)

    @Test
    fun cancellingTheRemovalKeepsTheTagOnTheHomePath() =
        assertCancellingRemovalKeepsTheTag(CategoryEntryPath.Home)

    @Test
    fun cancellingTheRemovalKeepsTheTagOnTheCategoriesPath() =
        assertCancellingRemovalKeepsTheTag(CategoryEntryPath.Categories)

    @Test
    fun confirmingTheRemovalDeletesTheTagOnTheHomePath() =
        assertConfirmingRemovalDeletesTheTag(CategoryEntryPath.Home)

    @Test
    fun confirmingTheRemovalDeletesTheTagOnTheCategoriesPath() =
        assertConfirmingRemovalDeletesTheTag(CategoryEntryPath.Categories)

    @Test
    fun blankAndDuplicateTagNamesAreRejectedOnTheHomePath() =
        assertTagNameErrors(CategoryEntryPath.Home)

    @Test
    fun blankAndDuplicateTagNamesAreRejectedOnTheCategoriesPath() =
        assertTagNameErrors(CategoryEntryPath.Categories)

    /** The last tag cannot move right, so its only move action is the left one. */
    private fun assertMovingATagLeftReorders(path: CategoryEntryPath) {
        createFixtureTags()
        showPager(path)
        swipeToTagPage(lastTag)

        moveTagRightButton(lastTag).assertIsNotEnabled()
        moveTagLeftButton(lastTag).performClick()

        composeTestRule.waitUntil(
            "$lastTag did not move before $firstTag.",
            SettleTimeoutMillis,
        ) { tagOrder().takeLast(2) == listOf(lastTag, firstTag) }

        // The pager keeps its page index, so the page that was showing $lastTag now shows the tag
        // that moved into that slot. That slot is the last tag page, so the tag now in it cannot
        // move right any more and can only move back to the left - the buttons have to follow the
        // persisted order rather than the tag that used to be there.
        composeTestRule.waitUntil(
            "the pager did not show $firstTag after the reorder.",
            SettleTimeoutMillis,
        ) { isShown(firstTag) }
        moveTagRightButton(firstTag).assertIsNotEnabled()
        moveTagLeftButton(firstTag).assertIsEnabled()
    }

    private fun assertCancellingRemovalKeepsTheTag(path: CategoryEntryPath) {
        createFixtureTags()
        showPager(path)
        swipeToTagPage(lastTag)
        openRemovalDialog(lastTag)

        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitUntil(
            "the removal dialog stayed open after Cancel.",
            SettleTimeoutMillis,
        ) { !isShown("Remove tag?") }

        assertTrue("$lastTag was deleted although the removal was cancelled.", lastTag in tagOrder())
        composeTestRule.onNodeWithText(lastTag).assertIsDisplayed()
    }

    private fun assertConfirmingRemovalDeletesTheTag(path: CategoryEntryPath) {
        createFixtureTags()
        showPager(path)
        swipeToTagPage(lastTag)
        openRemovalDialog(lastTag)

        composeTestRule.onNodeWithTag(ConfirmRemoveTagButtonTestTag).performClick()

        composeTestRule.waitUntil(
            "$lastTag was still persisted after the removal was confirmed.",
            SettleTimeoutMillis,
        ) { lastTag !in tagOrder() }
        // It was the last tag, so the page it occupied becomes the Uncategorized page. The
        // assertion goes through the page's own add-tag field: the test framework reports the
        // page's container node as not displayed while that field on it is displayed, so the
        // field is the reliable witness that the page is the one on screen.
        composeTestRule.waitUntil(
            "the pager did not fall back to Uncategorized after the page was removed.",
            SettleTimeoutMillis,
        ) { uncategorizedPageIsOnScreen() }
        composeTestRule.onNodeWithTag(TagNameInputTestTag).assertIsDisplayed()
    }

    /** The two error branches of the Uncategorized add control. */
    private fun assertTagNameErrors(path: CategoryEntryPath) {
        createFixtureTags()
        showPager(path)
        swipeToFinalPage()
        val storedTags = tagOrder()

        composeTestRule.onNodeWithTag(CreateTagButtonTestTag).performClick()
        composeTestRule.waitUntil(
            "a blank tag name was accepted.",
            SettleTimeoutMillis,
        ) { isShown("Tag name cannot be blank") }

        // Typing clears the previous error, and the duplicate is refused by the repository.
        composeTestRule.onNodeWithTag(TagNameInputTestTag).performTextInput(lastTag)
        composeTestRule.waitUntil(
            "the blank-name error survived the next input.",
            SettleTimeoutMillis,
        ) { !isShown("Tag name cannot be blank") }

        composeTestRule.onNodeWithTag(CreateTagButtonTestTag).performClick()
        composeTestRule.waitUntil(
            "a duplicate tag name was accepted.",
            SettleTimeoutMillis,
        ) { isShown("Tag already exists") }

        assertEquals(storedTags, tagOrder())
    }

    /**
     * Creates both fixture tags as the last tags, which is what makes [lastTag] immovable to the
     * right. It is called per test rather than from `@Before` because the pager's page count - and
     * with it what "the final page" means - depends on the persisted tag list.
     */
    private fun createFixtureTags() = runBlocking {
        assertTrue("$firstTag was not created", application.tagRepository.createTag(firstTag))
        assertTrue("$lastTag was not created", application.tagRepository.createTag(lastTag))
    }

    private fun openRemovalDialog(tag: String) {
        removeTagButton(tag).performClick()
        composeTestRule.waitUntil(
            "the removal confirmation did not open.",
            SettleTimeoutMillis,
        ) { isShown("Remove tag?") }
        composeTestRule
            .onNodeWithText("Remove $tag and all of its application assignments?")
            .assertIsDisplayed()
    }

    private fun showPager(path: CategoryEntryPath) {
        composeTestRule.setContent {
            when (path) {
                CategoryEntryPath.Home -> CategoryPager { _ -> Text("Home dashboard") }
                CategoryEntryPath.Categories -> ApplicationCategoriesScreen()
            }
        }
    }

    /** The persisted tag order, which is what the move and the removal actually change. */
    private fun tagOrder(): List<String> = runBlocking {
        application.tagRepository.tags.first().map { it.name }
    }

    /**
     * Whether [selector]'s node is on the page the pager is showing. The pager composes the
     * neighbouring pages as well, so being present in the semantics tree is not the same as being
     * shown: `isDisplayed` also requires the node to be placed and inside the visible window.
     */
    private fun isOnScreen(selector: SemanticsNodeInteraction): Boolean = selector.isDisplayed()

    private fun isShown(text: String): Boolean = isOnScreen(composeTestRule.onNodeWithText(text))

    private fun onPageTag(tag: String): SemanticsNodeInteraction =
        composeTestRule.onNodeWithText(tag)

    /**
     * The control named by [contentDescription] on the page showing that tag. The pager composes
     * the neighbouring pages too, so `MoveTagLeftButtonTestTag`, `MoveTagRightButtonTestTag` and
     * `RemoveTagButtonTestTag` match one control per composed page; the content description names
     * the tag, which makes the lookup pick exactly the control of the page under test.
     */
    private fun tagControl(testTag: String, contentDescription: String): SemanticsNodeInteraction =
        composeTestRule.onNode(hasTestTag(testTag) and hasContentDescription(contentDescription))

    private fun moveTagLeftButton(tag: String): SemanticsNodeInteraction =
        tagControl(MoveTagLeftButtonTestTag, "Move $tag left")

    private fun moveTagRightButton(tag: String): SemanticsNodeInteraction =
        tagControl(MoveTagRightButtonTestTag, "Move $tag right")

    private fun removeTagButton(tag: String): SemanticsNodeInteraction =
        tagControl(RemoveTagButtonTestTag, "Remove $tag")

    /**
     * Swipes the pager to the page showing [tag]. The pager can open on a page behind the tag
     * pages (the Categories entry path lands on Uncategorized while the tag flow is still
     * loading), so the helper rewinds to the first page first and then walks forward one page at a
     * time; the final `waitUntil` both waits for a late settle and names the page that never
     * appeared.
     */
    private fun swipeToTagPage(tag: String) {
        rewindToFirstPage()
        repeat(MaxPagerSwipes) {
            if (isShown(tag)) return
            swipeLeftOnce()
        }
        composeTestRule.waitUntil(
            "$tag was not displayed after $MaxPagerSwipes swipes.",
            SettleTimeoutMillis,
        ) { isShown(tag) }
        composeTestRule.waitForIdle()
        onPageTag(tag).assertIsDisplayed()
    }

    /**
     * Swipes back to the pager's first page. A swipe towards the start on the first page is a
     * no-op, which is what makes the rewind self-limiting wherever the pager opened; the pager's
     * own scroll offset says when the start is reached.
     */
    private fun rewindToFirstPage() {
        repeat(MaxPagerSwipes) {
            if (pagerScrollOffset() == 0f) return
            swipeRightOnce()
        }
    }

    /** The pager's scroll offset, which is zero only on its first page. */
    private fun pagerScrollOffset(): Float =
        composeTestRule.onNodeWithTag(CategoryPagerTestTag)
            .fetchSemanticsNode()
            .config[SemanticsProperties.HorizontalScrollAxisRange]
            .value()

    /**
     * Swipes towards Uncategorized until that page is shown instead of swiping a fixed number of
     * times, so the result does not depend on how fast the pager settles.
     */
    private fun swipeToFinalPage() {
        repeat(MaxPagerSwipes) {
            if (uncategorizedPageIsOnScreen()) return
            swipeLeftOnce()
        }
        composeTestRule.waitUntil(
            "Uncategorized was not displayed after $MaxPagerSwipes swipes; " +
                "the pager did not settle on its final page.",
            SettleTimeoutMillis,
        ) { uncategorizedPageIsOnScreen() }
        composeTestRule.waitForIdle()
    }

    private fun swipeLeftOnce() {
        composeTestRule.onNodeWithTag(CategoryPagerTestTag)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
    }

    private fun swipeRightOnce() {
        composeTestRule.onNodeWithTag(CategoryPagerTestTag)
            .performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
    }

    /**
     * Whether the pager is showing the Uncategorized page, which is what its own add-tag name
     * field witnesses: the field belongs to that page alone, and the framework reports it as
     * displayed only while the page is the one on screen.
     */
    private fun uncategorizedPageIsOnScreen(): Boolean =
        isOnScreen(composeTestRule.onNodeWithTag(TagNameInputTestTag))
}
