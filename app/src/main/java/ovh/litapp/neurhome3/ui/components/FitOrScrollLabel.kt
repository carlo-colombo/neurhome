package ovh.litapp.neurhome3.ui.components

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isUnspecified

/**
 * One bounded shrink step: 85% of the base font size. A label that is a little too long for its row
 * therefore still fits without moving, and a label that needs more than that shrinks to this size
 * and no further, so every row of a list ends up with one of two label sizes instead of a range of
 * them. The line height is deliberately left at the base value: it, not the font size, decides the
 * line box, so shrinking a label can never change the row's height or the pitch between rows.
 */
private const val ShrinkFactor = 0.85f

/** How long a scrolling label rests - before the first scroll and at every loop restart. */
private const val MarqueePauseMillis = 1_000

/**
 * A single-line label that renders the whole string instead of ellipsizing its tail.
 *
 * It picks between three behaviours by measuring [text] with [style] against the width the parent
 * actually offers, so nothing here is keyed on a screen size or a dp threshold:
 * - the base style when [text] fits at it - this is the unchanged case, and no animation runs;
 * - the base style shrunk by [ShrinkFactor] when that is enough to make [text] fit;
 * - the shrunk style, scrolled as a marquee, when even the shrunk text does not fit. The marquee
 *   pauses [MarqueePauseMillis] at the start of each pass, moves the tail into view and repeats.
 *   [basicMarquee] only translates the drawing, so the label keeps its measured height and its
 *   neighbours never move while it scrolls.
 *
 * The marquee is only applied when this composition animates at all: with the animator duration
 * scale at zero (reduced motion, and the setting CI zeroes on its emulator) the shrunk label is
 * simply clipped, which is deterministic and leaves no looping animation behind. Nothing here
 * starts a coroutine of its own, and [basicMarquee] cancels its animation when it leaves the
 * composition.
 *
 * The node still reports [text] in full to semantics: the scrolling and the clipping happen in the
 * drawing phase, so `onNodeWithText(text)` and screen readers see the whole label either way.
 *
 * [style] is expected to be fully specified (the call sites pass a Material typography style), as
 * it is measured as given and rendered as given; a partial style whose unspecified fields would be
 * filled in from `LocalTextStyle` at draw time could be measured narrower than it renders.
 */
@Composable
internal fun FitOrScrollLabel(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val textMeasurer = rememberTextMeasurer()
        val availableWidthPx = constraints.maxWidth
        val shrunkStyle = remember(style) { style.shrunkOnce() }
        val fitsAtBaseSize = fitsOnOneLine(textMeasurer, text, style, availableWidthPx)
        val styleUsed = if (fitsAtBaseSize) style else shrunkStyle
        val fitsShrunk = !fitsAtBaseSize && fitsOnOneLine(textMeasurer, text, shrunkStyle, availableWidthPx)
        val animates = motionEnabled()

        Text(
            text = text,
            style = styleUsed,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = if (fitsShrunk || !animates) {
                Modifier
            } else {
                Modifier.basicMarquee(
                    // Repeat for as long as the label is on screen: the tail is only readable while
                    // the text is scrolled past, so stopping after a few passes would hide it again.
                    iterations = Int.MAX_VALUE,
                    animationMode = MarqueeAnimationMode.Immediately,
                    initialDelayMillis = MarqueePauseMillis,
                    repeatDelayMillis = MarqueePauseMillis,
                )
            },
        )
    }
}

/** Whether [text] fits on one line at [style] within [availableWidthPx]. */
@Composable
private fun fitsOnOneLine(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    availableWidthPx: Int,
): Boolean = remember(textMeasurer, text, style, availableWidthPx) {
    // `softWrap = false` lays the string out on one line whatever the width, so the measured width
    // is the width the label would need; comparing it with the offered width is the fit test. (A
    // wrapping measurement reports the constrained width instead, and a clipped single line then
    // looks like it fitted.)
    val requiredWidthPx = textMeasurer.measure(
        text = text,
        style = style,
        overflow = TextOverflow.Clip,
        softWrap = false,
        maxLines = 1,
    ).size.width
    requiredWidthPx <= availableWidthPx
}

/**
 * Whether this composition is allowed to animate, i.e. whether it runs at a non-zero [MotionDurationScale].
 *
 * The window's `MotionDurationScale` is the factor every Compose animation multiplies its duration
 * by, and it is zero when the user turned the animator duration scale off (or a test runner did).
 * Compose 1.12 has no composition local that carries it, so read it from the composition's
 * coroutine context - the same element an `Animatable` resolves - which a test can pin by passing a
 * scale to `createAndroidComposeRule(effectContext = ...)`. A composition that provides no scale at
 * all is not a reduced-motion request, so it counts as animating.
 *
 * Reading it here also subscribes to the system setting, so turning animations off while the label
 * is on screen stops it.
 */
@Composable
private fun motionEnabled(): Boolean {
    val scaleFactor = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    return scaleFactor > 0f
}

/** [this] at [ShrinkFactor] of its font size, or unchanged when it carries no font size. */
private fun TextStyle.shrunkOnce(): TextStyle =
    if (fontSize.isUnspecified) this else copy(fontSize = fontSize * ShrinkFactor)
