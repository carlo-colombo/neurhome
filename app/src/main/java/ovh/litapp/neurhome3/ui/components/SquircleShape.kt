package ovh.litapp.neurhome3.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The shape a contact's picture is cut to: the superellipse `|x/a|^4 + |y/b|^4 = 1`, which is what
 * "squircle" names - a square whose corners turn away continuously instead of meeting a circular
 * arc at a seam.
 *
 * Compose ships no squircle (`RoundedCornerShape` is a rectangle with arcs, `CircleShape` is the
 * superellipse at exponent 2), so the curve is sampled here and stitched back together as cubic
 * Bézier segments. The outline is a [Outline.Generic] path, which `Modifier.clip` accepts; a
 * graphics layer caches the clipped outline per size, so the sampling cost is paid once per size,
 * not per frame.
 *
 * Deliberately a plain [Shape] and not a `RoundedCornerShape`: a corner radius is expressed in dp
 * and a squircle's "corner" is the whole side, so there is no radius to scale.
 */
internal object SquircleShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(squirclePath(size))

    override fun toString(): String = "SquircleShape"
}

/** The exponent of the superellipse: 2 is an ellipse, higher is squarer. */
private const val SQUIRCLE_EXPONENT = 4f

/**
 * How finely the curve is sampled: one cubic segment per sample, this many per quadrant.
 *
 * 16 already puts a sample every ~2 dp of a 50 dp avatar, and the segments interpolate the true
 * curve rather than cutting it - the error at the corner is far below a pixel.
 */
private const val SQUIRCLE_SEGMENTS_PER_QUADRANT = 16

/**
 * A closed [Path] of the squircle that fills [size], starting at the right edge's midpoint.
 *
 * Sampled parametrically - `x = a·sign(cos t)·|cos t|^(2/n)`, and the same in `y` - and joined with
 * Catmull-Rom tangents converted to cubic Béziers, so the outline is smooth and passes exactly
 * through the four edge midpoints (which is what makes the shape touch its bounds).
 */
internal fun squirclePath(size: Size): Path {
    val rx = size.width / 2f
    val ry = size.height / 2f
    val count = SQUIRCLE_SEGMENTS_PER_QUADRANT * 4
    // The superellipse's parametric exponent: 2/n, so n = 4 gives square roots.
    val power = 2f / SQUIRCLE_EXPONENT
    val points = List(count) { index ->
        val angle = 2.0 * PI * index / count
        Offset(
            x = rx + rx * signedPower(cos(angle).toFloat(), power),
            y = ry + ry * signedPower(sin(angle).toFloat(), power),
        )
    }
    return Path().apply {
        if (points.isEmpty()) return@apply
        moveTo(points[0].x, points[0].y)
        for (index in points.indices) {
            val previous = points[(index - 1 + count) % count]
            val start = points[index]
            val end = points[(index + 1) % count]
            val following = points[(index + 2) % count]
            // Uniform Catmull-Rom as a cubic Bézier: the control points sit a sixth of the way
            // along each neighbour tangent.
            cubicTo(
                start.x + (end.x - previous.x) / 6f,
                start.y + (end.y - previous.y) / 6f,
                end.x - (following.x - start.x) / 6f,
                end.y - (following.y - start.y) / 6f,
                end.x,
                end.y,
            )
        }
        close()
    }
}

/** `value^power`, keeping the sign: the parametric form needs it for the second and fourth quadrants. */
private fun signedPower(value: Float, power: Float): Float =
    if (value < 0f) -(-value).pow(power) else value.pow(power)
