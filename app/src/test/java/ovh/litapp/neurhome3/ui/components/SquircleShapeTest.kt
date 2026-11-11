package ovh.litapp.neurhome3.ui.components

import android.app.Application
import android.graphics.Region
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins the shape a contact's picture is cut to.
 *
 * [GraphicsMode.Mode.NATIVE] is required: the legacy graphics mode only records draw calls, so a
 * path assertion would be checked against a stub rasteriser and `Region.setPath` would answer
 * nothing useful. The squircle is defined by its curve, so what has to hold is geometric: the path
 * fills its bounds exactly, it reaches the middle of each edge, and it has left the corners empty -
 * which is the whole difference between this shape and a rounded rectangle of some radius.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SquircleShapeTest {

    private val density = Density(1f)

    private fun squircle(size: Size = Size(100f, 100f)): Path {
        val outline = SquircleShape.createOutline(size, LayoutDirection.Ltr, density)
        assertTrue("a squircle is a curve, not a rectangle or a rounded rect", outline is Outline.Generic)
        return (outline as Outline.Generic).path
    }

    private fun regionOf(path: Path, size: Size = Size(100f, 100f)): Region =
        Region().apply { setPath(path.asAndroidPath(), Region(0, 0, size.width.toInt(), size.height.toInt())) }

    @Test
    fun thePathFillsItsBounds() {
        val bounds = squircle().getBounds()

        assertEquals(0f, bounds.left, 0.01f)
        assertEquals(0f, bounds.top, 0.01f)
        assertEquals(100f, bounds.right, 0.01f)
        assertEquals(100f, bounds.bottom, 0.01f)
    }

    @Test
    fun thePathTouchesEveryEdgeMidpointAndLeavesTheCornersEmpty() {
        val region = regionOf(squircle())

        assertTrue("centre", region.contains(50, 50))
        assertTrue("right edge midpoint", region.contains(98, 50))
        assertTrue("top edge midpoint", region.contains(50, 2))
        assertTrue("left edge midpoint", region.contains(2, 50))
        assertTrue("bottom edge midpoint", region.contains(50, 98))
        assertFalse("top left corner", region.contains(1, 1))
        assertFalse("top right corner", region.contains(98, 1))
        assertFalse("bottom left corner", region.contains(1, 98))
        assertFalse("bottom right corner", region.contains(98, 98))
    }

    @Test
    fun theShapeScalesWithItsSize() {
        // A 2:1 box must still be a squircle of that box, not a square squircle centred in it.
        val region = regionOf(squircle(Size(200f, 100f)), Size(200f, 100f))

        assertTrue(region.contains(198, 50))
        assertTrue(region.contains(100, 2))
        assertFalse(region.contains(1, 1))
        assertFalse(region.contains(198, 1))
    }
}
