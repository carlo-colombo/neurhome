package ovh.litapp.neurhome3.ui.components.dicebear

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ovh.litapp.neurhome3.data.avatar.DiceBearStyle

/**
 * Pins the process-wide avatar cache the contact rows are drawn from, and that a scene it hands
 * out twice for the same style and seed is the same picture.
 *
 * [GraphicsMode.Mode.NATIVE] is required for the determinism assertion only: the legacy graphics
 * mode records draw calls without rasterising, so two blank bitmaps would compare equal and the
 * assertion would hold for a renderer that draws nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContactAvatarsTest {

    private val application: Application = RuntimeEnvironment.getApplication()

    @Test
    fun theSameStyleAndSeedIsResolvedOnce() {
        val avatars = ContactAvatars(application)
        val style = DiceBearStyle.SHADOWS

        assertNull("nothing is resolved before it is asked for", avatars.cached(SEED, style))

        val first = requireNotNull(avatars.scene(SEED, style))
        assertSame(first, avatars.cached(SEED, style))
        assertSame(
            "a row composed again must reuse the parsed scene, not re-resolve it",
            first,
            avatars.scene(SEED, style),
        )
    }

    @Test
    fun theKeyIncludesTheStyle() {
        val avatars = ContactAvatars(application)

        val shadows = requireNotNull(avatars.scene(SEED, DiceBearStyle.SHADOWS))

        assertNull(
            "the same seed in another style is another avatar, not this one",
            avatars.cached(SEED, DiceBearStyle.IDENTICON),
        )
        assertTrue(shadows !== avatars.scene(SEED, DiceBearStyle.IDENTICON))
    }

    @Test
    fun theCacheEvictsTheEntryAskedForLongestAgoAtCapacity() {
        val avatars = ContactAvatars(application)
        val style = DiceBearStyle.IDENTICON
        val seeds = (0 until CAPACITY).map { "contact-$it" }
        seeds.forEach { requireNotNull(avatars.scene(it, style)) }

        // Reading an entry marks it as used, so the second seed is now the oldest.
        assertNotNull(avatars.cached(seeds.first(), style))
        requireNotNull(avatars.scene("one-past-capacity", style))

        assertNotNull("the recently used entry survives", avatars.cached(seeds.first(), style))
        assertNull("the least recently used entry is evicted", avatars.cached(seeds[1], style))
        assertNotNull(avatars.cached("one-past-capacity", style))
    }

    @Test
    fun aStyleThatDrawsNothingTakesNoCacheEntry() {
        val avatars = ContactAvatars(application)

        assertNull(avatars.scene(SEED, DiceBearStyle.NONE))
        assertNull(avatars.cached(SEED, DiceBearStyle.NONE))
    }

    /**
     * Two caches, so the second render cannot reuse the first one's scene: this is what pins that
     * resolving the same style and seed twice draws the same picture, where the test above only
     * pins that one object is handed back twice.
     */
    @Test
    fun theSameSeedAndStyleDrawTheSamePictureTwice() {
        val pixels = render(ContactAvatars(application), DiceBearStyle.SHADOWS, SEED)

        assertArrayEquals(pixels, render(ContactAvatars(application), DiceBearStyle.SHADOWS, SEED))
        assertTrue(
            "two blank renders would compare equal, so the avatar must actually paint",
            pixels.count { (it ushr 24) and 0xFF > 0 } > pixels.size / 4,
        )
    }

    private fun render(avatars: ContactAvatars, style: DiceBearStyle, seed: String): IntArray {
        val scene = requireNotNull(avatars.scene(seed, style))
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            Canvas(bitmap.asImageBitmap()),
            Size(SIZE.toFloat(), SIZE.toFloat()),
        ) {
            drawDiceBear(scene)
        }
        val pixels = IntArray(SIZE * SIZE)
        bitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        return pixels
    }

    private companion object {
        const val SEED = "ada"

        /** The cache's own capacity: growing it is fine, but this test must move with it. */
        const val CAPACITY = 64

        const val SIZE = 48
    }
}
