package ovh.litapp.neurhome3.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.litapp.neurhome3.ComposeTestActivity
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.ui.INeurhomeViewModel

/**
 * The Home screen long press must open the same per-application configuration panel as the full
 * applications screen and the category screens. Both Home layouts (the top-apps list and the list
 * shown while the T9 filter is active) render through [HomeApplicationsList], so this pins the
 * panel opening there, the tag write once the row collapses, and the scroll container that keeps a
 * panel taller than the Home slot reachable instead of drawing over the keyboard.
 *
 * The last two tests pin the collapsed layout the long-press change claimed to preserve: the
 * column's `fillMaxSize()` keeps the list measured to the slot (instead of wrapping to the rows),
 * and the collapsed arrangement is still `SpaceEvenly` for the top apps and `Bottom` while
 * filtering.
 */
@RunWith(AndroidJUnit4::class)
class HomeApplicationsListTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>()

    private val app = Application(
        label = "Test app",
        packageName = "test.app",
        icon = null,
    )
    private val tagWrites = mutableListOf<Set<String>>()
    private val tagActions = INeurhomeViewModel.AppTagActions(
        setTags = { _, tags -> tagWrites += tags },
    )

    /**
     * Height of the slot the collapsed-list tests render into: taller than the three 70dp
     * collapsed rows, so both arrangements have free space to distribute. `heightIn(max = ...)`
     * takes this value over the incoming constraint, so the slot is the same on any device.
     */
    private val collapsedSlotHeight = 400.dp

    /** Tag of that slot, so the tests can measure the height the list claimed inside it. */
    private val collapsedSlotTag = "home-applications-slot"

    /**
     * Tolerance for the two free-space identities below. The bounds come out of the semantics tree
     * as whole pixels, so every measured edge rounds by up to half a pixel; the SpaceEvenly free
     * space is built from four gaps and three row pitches, which is at most eleven pixels of
     * accumulated rounding. A real spacing change moves the collapsed rows by tens of dp, so this
     * stays far below one. Expressing it in pixels keeps it independent of the screen density.
     */
    private val freeSpaceRoundingTolerance: Dp
        get() = with(composeTestRule.density) { 12f.toDp() }

    @Test
    fun longPressOpensTheConfigurationPanelInTheTopAppsList() {
        render(filtering = false)

        composeTestRule.onNode(hasSetTextAction()).assertDoesNotExist()

        longPress(app.label)

        composeTestRule.onNode(hasSetTextAction()).assertExists()
        composeTestRule.onNodeWithText("Work").assertExists()
    }

    @Test
    fun longPressOpensTheConfigurationPanelWhileFiltering() {
        render(filtering = true)

        composeTestRule.onNode(hasSetTextAction()).assertDoesNotExist()

        longPress(app.label)

        composeTestRule.onNode(hasSetTextAction()).assertExists()
        composeTestRule.onNodeWithText("Work").assertExists()
    }

    @Test
    fun selectingATagWhileFilteringPersistsOnCollapse() {
        render(filtering = true)

        longPress(app.label)
        composeTestRule.onNodeWithText("Work").performTouchInput { click() }
        longPress(app.label)

        assertEquals(listOf(setOf("Work")), tagWrites)
    }

    @Test
    fun anExpandedPanelTallerThanTheSlotIsScrollable() {
        val apps = (1..3).map { Application("App $it", "test.app.$it", icon = null) }
        composeTestRule.setContent {
            // Simulates the weight-bounded Home slot: shorter than three collapsed rows plus a
            // panel, so the container needs a scroll range once a row opens.
            Box(Modifier.height(220.dp)) {
                HomeApplicationsList(
                    list = apps,
                    onLaunch = { _, _ -> },
                    filtering = false,
                    managementActions = INeurhomeViewModel.AppManagementActions(),
                    tagActions = tagActions,
                    availableTags = listOf("Work"),
                )
            }
        }

        longPress("App 1")

        // Without a scrollable container this would fail: the panel's last control sits below the
        // 220dp slot and would be clipped.
        composeTestRule.onNodeWithContentDescription("Uninstall").performScrollTo().assertIsDisplayed()
    }

    /**
     * `HomeApplicationsList` now fills the slot with `fillMaxSize().verticalScroll(...)` instead of
     * a fixed column. `fillMaxSize` is what keeps the collapsed list measured to the slot: without
     * it the column wraps to the three collapsed rows and the slot - bounded from above only, as
     * the dashboard's weight slot does - shrinks with the content.
     */
    @Test
    fun collapsedListMeasuresToTheSlotInsteadOfWrappingToTheRows() {
        val apps = threeCollapsedApps()
        composeTestRule.setContent {
            // The slot only bounds the height from above; Box passes that bound on with a zero
            // minimum, which is the constraint `fillMaxSize` resolves. Because Box also measures
            // to its single child, this slot reports the height the list itself claimed.
            Box(
                Modifier
                    .heightIn(max = collapsedSlotHeight)
                    .testTag(collapsedSlotTag)
            ) {
                HomeApplicationsList(
                    list = apps,
                    onLaunch = { _, _ -> },
                    filtering = false,
                    managementActions = INeurhomeViewModel.AppManagementActions(),
                    tagActions = tagActions,
                    availableTags = listOf("Work"),
                )
            }
        }

        // Three collapsed rows measure 3 * 70dp, so a wrapping column leaves the slot at ~210dp.
        composeTestRule.onNodeWithTag(collapsedSlotTag).assertHeightIsEqualTo(collapsedSlotHeight)
    }

    /**
     * With every row collapsed the top-apps list is still `Arrangement.SpaceEvenly` and the
     * filtering list is still `Arrangement.Bottom`. In a slot tall enough to hold the three
     * fixed-height rows with room to spare, SpaceEvenly spreads them with a gap of
     * `(slot - 3 * rowHeight) / 4`, while Bottom leaves that whole space above them and packs the
     * rows against the slot bottom.
     */
    @Test
    fun collapsedTopAppsKeepSpaceEvenlyWhileFilteringKeepsBottom() {
        val apps = threeCollapsedApps()
        val filtering = mutableStateOf(false)
        composeTestRule.setContent {
            Box(
                Modifier
                    .heightIn(max = collapsedSlotHeight)
                    .testTag(collapsedSlotTag)
            ) {
                HomeApplicationsList(
                    list = apps,
                    onLaunch = { _, _ -> },
                    filtering = filtering.value,
                    managementActions = INeurhomeViewModel.AppManagementActions(),
                    tagActions = tagActions,
                    availableTags = listOf("Work"),
                )
            }
        }

        val slot = composeTestRule.onNodeWithTag(collapsedSlotTag).getUnclippedBoundsInRoot()
        val spread = collapsedRows(apps)
        filtering.value = true
        composeTestRule.waitForIdle()
        val pinned = collapsedRows(apps)

        spread.assertEvenPitch("SpaceEvenly")
        pinned.assertEvenPitch("Bottom")

        val gap = spread.pitch - pinned.pitch
        assertTrue(
            "Bottom must still pack the collapsed rows closer than SpaceEvenly spreads them",
            gap > 0.dp
        )
        // Bottom leaves the rows packed, so their pitch is the collapsed row height and the four
        // equal SpaceEvenly gaps have to add up to the slot minus the three rows.
        (gap * 4).assertIsEqualTo(
            collapsedSlotHeight - pinned.pitch * 3,
            "SpaceEvenly free space inside the slot",
            freeSpaceRoundingTolerance,
        )
        // Bottom moves the rows down by the whole free space, so the last row keeps the distance
        // to the slot bottom that SpaceEvenly's own bottom gap had, minus that one gap.
        (slot.bottom - spread.last().bottom - gap).assertIsEqualTo(
            slot.bottom - pinned.last().bottom,
            "slot space below the last collapsed row",
            freeSpaceRoundingTolerance,
        )
    }

    /** The three collapsed rows, in list order, as they are laid out in the root composable. */
    private fun collapsedRows(apps: List<Application>): List<DpRect> =
        apps.map { composeTestRule.onNodeWithText(it.label).getUnclippedBoundsInRoot() }

    /** Distance between the tops of two neighbouring collapsed rows: row height plus gap. */
    private val List<DpRect>.pitch: Dp
        get() = this[1].top - this[0].top

    private fun List<DpRect>.assertEvenPitch(arrangement: String) {
        pitch.assertIsEqualTo(this[2].top - this[1].top, "$arrangement collapsed row pitch")
    }

    private fun threeCollapsedApps() =
        (1..3).map { Application("App $it", "test.app.$it", icon = null) }

    private fun render(filtering: Boolean) {
        composeTestRule.setContent {
            HomeApplicationsList(
                list = listOf(app),
                onLaunch = { _, _ -> },
                filtering = filtering,
                managementActions = INeurhomeViewModel.AppManagementActions(),
                tagActions = tagActions,
                availableTags = listOf("Work"),
            )
        }
    }

    private fun longPress(label: String) {
        composeTestRule.onNodeWithText(label).performTouchInput { longClick() }
    }
}
