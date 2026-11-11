package ovh.litapp.neurhome3.data.avatar

/**
 * One style definition resolved for one seed: a flat tree the renderer can draw without
 * asking the PRNG anything.
 *
 * Kept free of Compose and Android types so the resolution can be tested (and diffed
 * against the reference implementation) on a plain JVM, and so a resolved avatar can be
 * remembered across recompositions without holding graphics objects.
 */
data class ResolvedAvatar(
    val width: Float,
    val height: Float,
    /** Packed ARGB of the canvas background, or null when the style paints none. */
    val background: Int?,
    val nodes: List<ResolvedNode>,
)

/** The PRNG-derived choices behind a [ResolvedAvatar], in the reference's own vocabulary. */
data class ResolvedStyleOptions(
    val components: Map<String, ResolvedComponentOptions>,
    /** Resolved palette per colour group, as lowercase hex; a group with no palette is empty. */
    val colors: Map<String, List<String>>,
)

data class ResolvedComponentOptions(
    val visible: Boolean,
    val variant: String?,
    val rotate: Double,
    val translateX: Double,
    val translateY: Double,
    val scale: Double,
)

sealed interface ResolvedNode {
    val transform: AffineTransform?

    /** Group opacity: the reference wraps such a node in a layer, so it must not be folded per shape. */
    val opacity: Float

    val blendMode: ResolvedBlendMode?
}

/** A `g` element: paint attributes apply to its children, so only the transform and layer apply. */
data class ResolvedGroup(
    override val transform: AffineTransform?,
    override val opacity: Float,
    override val blendMode: ResolvedBlendMode?,
    val children: List<ResolvedNode>,
) : ResolvedNode

data class ResolvedShape(
    override val transform: AffineTransform?,
    override val opacity: Float,
    override val blendMode: ResolvedBlendMode?,
    val geometry: ResolvedGeometry,
    val fill: ResolvedPaint?,
    val stroke: ResolvedStroke?,
    /** `fill-rule="evenodd"`: inner subpaths punch holes instead of filling. */
    val evenOdd: Boolean,
) : ResolvedNode

data class ResolvedPaint(val color: Int, val alpha: Float)

data class ResolvedStroke(
    val paint: ResolvedPaint,
    val width: Float,
    val cap: ResolvedStrokeCap,
    val join: ResolvedStrokeJoin,
)

enum class ResolvedStrokeCap { BUTT, ROUND, SQUARE }

enum class ResolvedStrokeJoin { MITER, ROUND, BEVEL }

/** The blend modes the vendored styles use through `style="mix-blend-mode:…"`. */
enum class ResolvedBlendMode { OVERLAY, LIGHTEN }

sealed interface ResolvedGeometry {
    /** Raw SVG path data, parsed by `androidx.compose.ui.graphics.vector.PathParser` at draw time. */
    data class PathData(val data: String) : ResolvedGeometry

    data class Circle(val cx: Float, val cy: Float, val radius: Float) : ResolvedGeometry

    data class Ellipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : ResolvedGeometry

    data class Rect(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val rx: Float,
        val ry: Float,
    ) : ResolvedGeometry
}

/**
 * An SVG 2D affine transform: `x' = a·x + c·y + e`, `y' = b·x + d·y + f`.
 *
 * A transform list composes left to right - `"translate(10) scale(2)"` scales first, then
 * translates - so [times] means "apply [other] first".
 */
data class AffineTransform(
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val e: Float,
    val f: Float,
) {
    operator fun times(other: AffineTransform): AffineTransform = AffineTransform(
        a = a * other.a + c * other.b,
        b = b * other.a + d * other.b,
        c = a * other.c + c * other.d,
        d = b * other.c + d * other.d,
        e = a * other.e + c * other.f + e,
        f = b * other.e + d * other.f + f,
    )

    companion object {
        val IDENTITY = AffineTransform(1f, 0f, 0f, 1f, 0f, 0f)

        fun translate(tx: Float, ty: Float) = AffineTransform(1f, 0f, 0f, 1f, tx, ty)

        fun scale(sx: Float, sy: Float) = AffineTransform(sx, 0f, 0f, sy, 0f, 0f)

        fun rotate(degrees: Float): AffineTransform {
            val radians = Math.toRadians(degrees.toDouble())
            val cos = kotlin.math.cos(radians).toFloat()
            val sin = kotlin.math.sin(radians).toFloat()
            return AffineTransform(cos, sin, -sin, cos, 0f, 0f)
        }

        /** Rotation about `(cx, cy)`, which is how every vendored `rotate(...)` is written. */
        fun rotate(degrees: Float, cx: Float, cy: Float): AffineTransform =
            translate(cx, cy) * rotate(degrees) * translate(-cx, -cy)
    }
}
