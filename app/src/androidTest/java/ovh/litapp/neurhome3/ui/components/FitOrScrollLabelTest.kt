package ovh.litapp.neurhome3.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ovh.litapp.neurhome3.ComposeTestActivity
import ovh.litapp.neurhome3.ui.theme.Neurhome3Theme

/** The shrink step [FitOrScrollLabel] applies before it scrolls: 85% of the base font size. */
private const val ShrunkFraction = 0.85f

/** A label short enough for any of the container widths below. */
private const val FittingLabel = "Fit"

/** A label that does not fit even at 85% in any of the container widths below. */
private const val OverflowingLabel = "The whole label must stay readable"

/** The text a plain, unshrunk `Text` renders, so the expected base size comes from the composition. */
private const val ReferenceLabel = "reference"

/** Wide enough for [FittingLabel] at the base size on any density. */
private val WideBox = 200.dp

/** Narrow enough that [OverflowingLabel] overflows at the base size and at the shrunk one. */
private val NarrowBox = 60.dp

private const val LabelBoxTestTag = "fit-or-scroll-label"

/** Long enough for the marquee's start pause plus a visible part of a pass. */
private const val PastTheInitialPauseMillis = 2_500L

/**
 * Pins [FitOrScrollLabel]'s three behaviours - unchanged when it fits, shrunk when that is all it
 * needs, marqueed when even the shrunk text does not fit - and that the last one only animates when
 * the composition is allowed to.
 *
 * The animation is driven by [MotionDurationScale] on the composition's context, which is the same
 * element [FitOrScrollLabel] reads and the seam Compose documents for controlling motion in tests
 * (`createAndroidComposeRule(effectContext = ...)`). Pinning it here makes both modes testable in
 * one run, whatever the emulator's own animator duration scale is set to.
 *
 * The base font size is read from a plain `Text` in the same composition instead of a typography
 * constant, so the test does not depend on which typography the theme installs.
 */
@RunWith(AndroidJUnit4::class)
class FitOrScrollLabelTest {
    private val motionScale = TestMotionDurationScale()

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComposeTestActivity>(effectContext = motionScale)

    @Before
    fun allowMotion() {
        motionScale.scaleFactor = 1f
    }

    @Test
    fun aLabelThatFitsKeepsTheBaseSizeAndDoesNotMove() {
        composeTestRule.mainClock.autoAdvance = false
        showLabel(FittingLabel, WideBox)
        val container = composeTestRule.onNodeWithTag(LabelBoxTestTag)
        val before = container.captureToImage()

        composeTestRule.mainClock.advanceTimeBy(PastTheInitialPauseMillis)
        composeTestRule.waitForIdle()

        assertEquals(
            "a label that fits must be drawn at the base size, not the shrunk one",
            referenceFontSize(),
            composeTestRule.onNodeWithText(FittingLabel).fontSize(),
        )
        assertEquals(
            "a label that fits must not scroll",
            0,
            changedPixels(before, container.captureToImage()),
        )
    }

    @Test
    fun aLabelTooLongToFitIsDrawnAtTheShrunkSize() {
        composeTestRule.mainClock.autoAdvance = false
        showLabel(OverflowingLabel, NarrowBox)

        val baseSize = referenceFontSize()
        val labelSize = composeTestRule.onNodeWithText(OverflowingLabel).fontSize()

        assertEquals(
            "a label too long for the base size must shrink to $ShrunkFraction of it",
            baseSize * ShrunkFraction,
            labelSize,
        )
        assertTrue("the label must actually be smaller: $labelSize", labelSize < baseSize)
    }

    @Test
    fun aLabelThatStillDoesNotFitScrollsWhileMotionIsAllowed() {
        composeTestRule.mainClock.autoAdvance = false
        showLabel(OverflowingLabel, NarrowBox)
        val container = composeTestRule.onNodeWithTag(LabelBoxTestTag)

        val boundsBefore = container.getUnclippedBoundsInRoot()
        val before = container.captureToImage()

        composeTestRule.mainClock.advanceTimeBy(PastTheInitialPauseMillis)
        composeTestRule.waitForIdle()

        assertTrue(
            "the tail of a label that does not fit must be scrolled into view",
            changedPixels(before, container.captureToImage()) > 0,
        )
        assertEquals(
            "scrolling must not move or resize the label's container",
            boundsBefore,
            container.getUnclippedBoundsInRoot(),
        )
    }

    @Test
    fun aLabelThatStillDoesNotFitStaysStillWhenMotionIsOff() {
        motionScale.scaleFactor = 0f
        composeTestRule.mainClock.autoAdvance = false
        showLabel(OverflowingLabel, NarrowBox)
        val container = composeTestRule.onNodeWithTag(LabelBoxTestTag)
        val before = container.captureToImage()

        composeTestRule.mainClock.advanceTimeBy(PastTheInitialPauseMillis)
        composeTestRule.waitForIdle()

        assertEquals(
            "reduced motion keeps the shrunk size",
            referenceFontSize() * ShrunkFraction,
            composeTestRule.onNodeWithText(OverflowingLabel).fontSize(),
        )
        assertEquals(
            "reduced motion must not animate the label",
            0,
            changedPixels(before, container.captureToImage()),
        )
    }

    @Test
    fun theWholeLabelStaysInTheSemantics() {
        composeTestRule.mainClock.autoAdvance = false
        showLabel(OverflowingLabel, NarrowBox)

        val node = composeTestRule.onNodeWithText(OverflowingLabel).fetchSemanticsNode()

        assertEquals(
            "a clipped or scrolled label must still report the whole string",
            OverflowingLabel,
            node.config[SemanticsProperties.Text].joinToString("") { it.text },
        )
    }

    /**
     * Renders [label] in a [width]-wide box, next to a [ReferenceLabel] drawn at the same style
     * with no fitting logic in the way.
     */
    private fun showLabel(label: String, width: Dp) {
        composeTestRule.setContent {
            Neurhome3Theme {
                Column {
                    Text(text = ReferenceLabel, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    Box(modifier = Modifier.width(width).testTag(LabelBoxTestTag)) {
                        FitOrScrollLabel(text = label, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }

    private fun referenceFontSize(): TextUnit =
        composeTestRule.onNodeWithText(ReferenceLabel).fontSize()

    /** The font size this node was actually laid out at. */
    private fun SemanticsNodeInteraction.fontSize(): TextUnit {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().layoutInput.style.fontSize
    }
}

/** The number of pixels that differ between two captures of the same region. */
private fun changedPixels(before: ImageBitmap, after: ImageBitmap): Int {
    val beforePixels = before.toPixelMap()
    val afterPixels = after.toPixelMap()
    require(beforePixels.width == afterPixels.width && beforePixels.height == afterPixels.height) {
        "the captured region changed size: ${beforePixels.width}x${beforePixels.height} -> " +
            "${afterPixels.width}x${afterPixels.height}"
    }
    var changed = 0
    for (y in 0 until beforePixels.height) {
        for (x in 0 until beforePixels.width) {
            if (beforePixels[x, y] != afterPixels[x, y]) changed++
        }
    }
    return changed
}

/**
 * The motion duration scale the test composition runs at. A plain field, not snapshot state: each
 * test picks its mode before the content is composed.
 */
private class TestMotionDurationScale : MotionDurationScale {
    override var scaleFactor: Float = 1f
}
