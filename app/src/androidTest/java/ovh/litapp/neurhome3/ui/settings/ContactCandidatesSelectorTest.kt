package ovh.litapp.neurhome3.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.litapp.neurhome3.ComposeTestActivity
import ovh.litapp.neurhome3.data.repositories.ContactCandidates

/**
 * Drives the stateless contact-scope selector, which is what the Settings screen renders once the
 * contact permission has been resolved. The permission wrapper itself is not under test here: it
 * only decides *when* the selection is written.
 */
@RunWith(AndroidJUnit4::class)
class ContactCandidatesSelectorTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun contactsAreOffByDefault() {
        render(selection = ContactCandidates.NONE)

        composeTestRule.onNodeWithTag(ContactCandidatesSelectorTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ContactCandidatesNoneTestTag).assertIsSelected()
        composeTestRule.onNodeWithTag(ContactCandidatesStarredTestTag).assertIsNotSelected()
        composeTestRule.onNodeWithTag(ContactCandidatesAllTestTag).assertIsNotSelected()
    }

    @Test
    fun thePersistedScopeIsRestored() {
        render(selection = ContactCandidates.ALL)

        composeTestRule.onNodeWithTag(ContactCandidatesAllTestTag).assertIsSelected()
        composeTestRule.onNodeWithTag(ContactCandidatesNoneTestTag).assertIsNotSelected()
        composeTestRule.onNodeWithTag(ContactCandidatesStarredTestTag).assertIsNotSelected()
    }

    @Test
    fun choosingAllContactsReportsItAndLeavesStarredUnselected() {
        var selected = ContactCandidates.STARRED
        render(selection = ContactCandidates.STARRED, onSelect = { selected = it })

        composeTestRule.onNodeWithTag(ContactCandidatesAllTestTag).performClick()

        assertEquals(ContactCandidates.ALL, selected)
        composeTestRule.onNodeWithTag(ContactCandidatesAllTestTag).assertIsSelected()
        composeTestRule.onNodeWithTag(ContactCandidatesStarredTestTag).assertIsNotSelected()
    }

    @Test
    fun choosingOffReportsIt() {
        var selected = ContactCandidates.ALL
        render(selection = ContactCandidates.ALL, onSelect = { selected = it })

        composeTestRule.onNodeWithTag(ContactCandidatesNoneTestTag).performClick()

        assertEquals(ContactCandidates.NONE, selected)
        composeTestRule.onNodeWithTag(ContactCandidatesNoneTestTag).assertIsSelected()
    }

    private fun render(
        selection: ContactCandidates,
        onSelect: (ContactCandidates) -> Unit = {},
    ) {
        composeTestRule.setContent {
            var current by remember { mutableStateOf(selection) }
            ContactCandidatesSelector(
                selection = current,
                onSelect = {
                    current = it
                    onSelect(it)
                }
            )
        }
    }
}
