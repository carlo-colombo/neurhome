package ovh.litapp.neurhome3.data.avatar

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.io.File
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
 * Exports the sample grid in `docs/avatars/samples/`: every vendored style at 200x200 for a few
 * seeds, next to the reference image the DiceBear API renders for the same style and seed.
 *
 * This is the regeneration command for the `ref-`-less files in that directory (see its README).
 * The `ref-*.png` files are the API's own output and are not written here - they are refreshed by
 * the documented `curl` loop, which needs the network, and this test must not.
 *
 * It writes into the repository on purpose: the point of the grid is that a reviewer can look at
 * the renderer's output without building the app. A change in Skia's anti-aliasing after a
 * Robolectric bump therefore shows up as a diff in the samples, which is the intended review
 * signal rather than a failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiceBearSampleExportTest {

    @Test
    fun writesTheSampleGrid() {
        val directory = samplesDirectory()
        for (style in DiceBearStyle.entries - DiceBearStyle.NONE) {
            for (seed in SEEDS) {
                val file = File(directory, "${style.id}-$seed.png")
                java.io.FileOutputStream(file).use { render(style, seed).compress(Bitmap.CompressFormat.PNG, 100, it) }
                assertTrue("${file.name} is empty", file.length() > MINIMUM_PNG_BYTES)
            }
        }
    }

    /** Walks up from the test's working directory (`app/`) to the directory that holds the grid. */
    private fun samplesDirectory(): File {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, SAMPLES_DIRECTORY)
            if (candidate.isDirectory) return candidate
            directory = directory.parentFile
        }
        throw AssertionError("no $SAMPLES_DIRECTORY above ${File("").absolutePath}")
    }

    private fun render(style: DiceBearStyle, seed: String): Bitmap {
        val avatar = requireNotNull(
            style.resolve(RuntimeEnvironment.getApplication(), seed),
        ) { "$style/$seed does not resolve" }
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(
            Density(1f),
            LayoutDirection.Ltr,
            Canvas(bitmap.asImageBitmap()),
            Size(SIZE.toFloat(), SIZE.toFloat()),
        ) {
            drawDiceBear(diceBearScene(avatar))
        }
        return bitmap
    }

    private companion object {
        const val SIZE = 200
        const val SAMPLES_DIRECTORY = "docs/avatars/samples"
        const val MINIMUM_PNG_BYTES = 512L

        /** The same seeds the reference images were downloaded for. */
        val SEEDS = listOf("Ada", "+33612345678", "🤖")
    }
}
