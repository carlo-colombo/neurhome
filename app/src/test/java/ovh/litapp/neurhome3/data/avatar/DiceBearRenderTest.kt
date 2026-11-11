package ovh.litapp.neurhome3.data.avatar

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ovh.litapp.neurhome3.ui.components.dicebear.diceBearScene
import ovh.litapp.neurhome3.ui.components.dicebear.drawDiceBear

/**
 * Proves the renderer actually rasterises, and rasterises the same picture the reference
 * implementation produces.
 *
 * [GraphicsMode.Mode.NATIVE] is required and must not be "simplified" to the legacy mode: the
 * legacy Canvas only records draw calls, so a determinism or non-blank assertion would pass
 * against a renderer that draws nothing at all, and a pixel comparison would be meaningless.
 * Native graphics runs the real Skia path (`Bitmap` + `Canvas` + `Path`).
 *
 * The reference images in `resources/dicebear/reference/` were downloaded once from
 * `https://api.dicebear.com/10.x/<style>/png?seed=<seed>&size=100` (resvg rasterisation); the
 * test never touches the network. Both sides are composited over white first, because a style
 * without a background palette (identicon) draws on transparency.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiceBearRenderTest {

    private val application: Application = RuntimeEnvironment.getApplication()

    @Test
    fun theSameSeedRendersIdenticallyTwice() {
        for (style in renderedStyles()) {
            for (seed in SEEDS) {
                assertArrayEquals(
                    "$style/$seed is not deterministic",
                    render(style, seed),
                    render(style, seed),
                )
            }
        }
    }

    @Test
    fun everyStyleCoversAMeaningfulShareOfTheAvatar() {
        for (style in renderedStyles()) {
            for (seed in SEEDS) {
                val pixels = render(style, seed)
                // The sparsest vendored style (identicon, a 5x5 cell grid) still paints over
                // half of its canvas, so a quarter is a floor no working renderer can miss.
                val painted = pixels.count { (it ushr 24) and 0xFF > 0 }
                assertTrue(
                    "$style/$seed painted only $painted of ${pixels.size} pixels",
                    painted > pixels.size / 4,
                )
            }
        }
    }

    /**
     * Compares every style against the reference rasterisation.
     *
     * The tolerance is measured, not guessed. Over these ten styles and two seeds the worst
     * case is identicon (2.8% of pixels off by more than 32 on some channel, mean channel
     * delta 1.66): identicon sets `shape-rendering="crispEdges"`, which makes the reference
     * snap cell edges to whole pixels while this renderer anti-aliases them, so the mismatch
     * is one blended pixel per cell boundary and nothing else. Every other style stays under
     * 1% and a mean delta below 1.6. A real regression - a lost blend layer, a shuffled
     * palette, a wrong transform - moves whole regions and lands far outside both bounds.
     */
    @Test
    fun everyStyleMatchesTheReferenceRasterisation() {
        for (style in renderedStyles()) {
            for (seed in SEEDS) {
                val ours = render(style, seed)
                val reference = reference("${style.id}-$seed")
                assertEquals("${style.id}-$seed size", reference.size, ours.size)

                var offByMoreThan32 = 0
                var totalDelta = 0L
                for (index in ours.indices) {
                    val worst = maxChannelDelta(ours[index], reference[index])
                    if (worst > 32) offByMoreThan32++
                    totalDelta += worst
                }
                val share = offByMoreThan32.toDouble() / ours.size * 100
                val mean = totalDelta.toDouble() / ours.size
                assertTrue(
                    "$style/$seed differs from the reference image on %.2f%% of pixels (limit 4%%)".format(share),
                    share <= MAX_MISMATCH_SHARE_PERCENT,
                )
                assertTrue(
                    "$style/$seed has a mean channel delta of %.2f (limit %.1f)".format(mean, MAX_MEAN_CHANNEL_DELTA),
                    mean <= MAX_MEAN_CHANNEL_DELTA,
                )
            }
        }
    }

    /**
     * Masks, gradients, filters and text are out of scope: a style that needs them must be
     * reported, not drawn with the construct missing. The ten vendored definitions stay inside
     * the supported set, and resolving them - which throws on anything unsupported - proves it
     * end to end rather than by string matching alone.
     */
    @Test
    fun theVendoredDefinitionsUseOnlySupportedConstructs() {
        val drawableElements = setOf("path", "circle", "ellipse", "rect", "g")
        for (style in renderedStyles()) {
            val definition = requireNotNull(style.definition(application))
            definition.canvas.elements.forEach { element ->
                walk(element, insideStyle = false) { node, inStyle ->
                    when (node.type) {
                        StyleElementType.ELEMENT -> {
                            val name = requireNotNull(node.name) { "$style: an element without a name" }
                            if (name == "style") {
                                // CSS for the animated variants, which are never selected.
                                assertTrue("$style: <style> must only contain text", node.attributes.isEmpty())
                                return@walk
                            }
                            assertTrue(
                                "$style: <$name> is not supported",
                                name in drawableElements,
                            )
                        }

                        StyleElementType.COMPONENT -> requireNotNull(node.name) { "$style: a component without a name" }
                        StyleElementType.TEXT -> assertTrue(
                            "$style: text content outside a <style> element is not supported",
                            inStyle,
                        )
                    }
                    for ((key, value) in node.attributes) {
                        if (value is StyleAttributeValue.VariableReference) {
                            throw AssertionError("$style: attribute '$key' references a variable")
                        }
                        val literal = (value as? StyleAttributeValue.Literal)?.value ?: continue
                        assertTrue(
                            "$style: attribute '$key' references an external resource ('$literal')",
                            !literal.contains("url("),
                        )
                        if (key == "style") {
                            assertTrue(
                                "$style: unsupported style declaration '$literal'",
                                literal.split(';').all { declaration ->
                                    declaration.isBlank() || declaration.trim().startsWith("mix-blend-mode:")
                                },
                            )
                        }
                    }
                }
            }
            // The resolver rejects unsupported geometry, attributes and references as it walks.
            for (seed in SEEDS) {
                requireNotNull(style.resolve(application, seed))
            }
        }
    }

    private fun walk(
        element: StyleElement,
        insideStyle: Boolean,
        visit: (StyleElement, Boolean) -> Unit,
    ) {
        visit(element, insideStyle)
        val nested = insideStyle || (element.type == StyleElementType.ELEMENT && element.name == "style")
        element.children.forEach { walk(it, nested, visit) }
    }

    private fun renderedStyles(): List<DiceBearStyle> = DiceBearStyle.entries - DiceBearStyle.NONE

    private fun render(style: DiceBearStyle, seed: String): IntArray {
        val avatar = requireNotNull(style.resolve(application, seed)) { "$style/$seed does not resolve" }
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            Canvas(bitmap.asImageBitmap()),
            Size(SIZE.toFloat(), SIZE.toFloat()),
        ) {
            drawDiceBear(diceBearScene(avatar))
        }
        val pixels = IntArray(SIZE * SIZE)
        bitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        return compositeOverWhite(pixels)
    }

    private fun reference(name: String): IntArray {
        val stream = requireNotNull(javaClass.getResourceAsStream("/dicebear/reference/$name.png")) {
            "reference image $name.png is missing"
        }
        val image: BufferedImage = stream.use { ImageIO.read(it) }
        val pixels = IntArray(image.width * image.height)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                pixels[y * image.width + x] = image.getRGB(x, y)
            }
        }
        return compositeOverWhite(pixels)
    }

    /** Both sides are flattened onto white so a transparent background compares as white. */
    private fun compositeOverWhite(pixels: IntArray): IntArray = IntArray(pixels.size) { index ->
        val argb = pixels[index]
        val alpha = (argb ushr 24) and 0xFF
        fun over(shift: Int): Int = (((argb shr shift) and 0xFF) * alpha + 255 * (255 - alpha) + 127) / 255
        (0xFF shl 24) or (over(16) shl 16) or (over(8) shl 8) or over(0)
    }

    private fun maxChannelDelta(left: Int, right: Int): Int =
        intArrayOf(16, 8, 0).maxOf { shift ->
            kotlin.math.abs(((left shr shift) and 0xFF) - ((right shr shift) and 0xFF))
        }

    private companion object {
        const val SIZE = 100
        val SEEDS = listOf("Ada", "+33612345678")
        const val MAX_MISMATCH_SHARE_PERCENT = 4.0
        const val MAX_MEAN_CHANNEL_DELTA = 3.0
    }
}
