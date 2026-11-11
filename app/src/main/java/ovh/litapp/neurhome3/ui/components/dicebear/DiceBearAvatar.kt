package ovh.litapp.neurhome3.ui.components.dicebear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import ovh.litapp.neurhome3.application.NeurhomeApplication
import ovh.litapp.neurhome3.data.avatar.AffineTransform
import ovh.litapp.neurhome3.data.avatar.DiceBearStyle
import ovh.litapp.neurhome3.data.avatar.ResolvedAvatar
import ovh.litapp.neurhome3.data.avatar.ResolvedBlendMode
import ovh.litapp.neurhome3.data.avatar.ResolvedGeometry
import ovh.litapp.neurhome3.data.avatar.ResolvedGroup
import ovh.litapp.neurhome3.data.avatar.ResolvedNode
import ovh.litapp.neurhome3.data.avatar.ResolvedPaint
import ovh.litapp.neurhome3.data.avatar.ResolvedShape
import ovh.litapp.neurhome3.data.avatar.ResolvedStrokeCap
import ovh.litapp.neurhome3.data.avatar.ResolvedStrokeJoin
import ovh.litapp.neurhome3.ui.components.SquircleShape

/**
 * A deterministic, offline avatar for [seed] drawn from a vendored DiceBear style.
 *
 * The seed decides everything: the same seed and style always produce the same picture, on
 * any device and with no network, which is what lets a contact row fall back to a stable
 * avatar without sending the contact's name anywhere.
 *
 * The picture is cut to a squircle here rather than at every call site: a style draws a square
 * body (the reference clips it at border radius 0) and the shape is part of what a contact's
 * picture looks like, so no caller may forget it.
 */
@Composable
fun DiceBearAvatar(
    seed: String,
    style: DiceBearStyle,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val avatars = remember(context) {
        (context.applicationContext as? NeurhomeApplication)?.contactAvatars
    }
    // Resolving is a JSON-driven PRNG walk and the draw pass runs on every frame, so the
    // scene - parsed paths included - is built once per seed and style, not per draw. It is
    // asked of the process-wide cache first: a scrolling contact list composes the same row
    // again and again, and only the first composition should pay for the parse. A composition
    // without the application (a preview, an instrumented test) resolves directly.
    val scene = remember(seed, style, avatars) {
        avatars?.scene(seed, style) ?: style.resolve(context, seed)?.let(::diceBearScene)
    }
    Canvas(modifier = modifier.size(size).clip(SquircleShape)) {
        scene?.let { drawDiceBear(it) }
    }
}

/**
 * A [ResolvedAvatar] with its geometry turned into [Path]s.
 *
 * Path data is parsed once here rather than on every draw: a scrolling contact list draws
 * these avatars every frame, and a style's variant body holds dozens of paths.
 */
internal class DiceBearScene(
    val width: Float,
    val height: Float,
    val background: Color?,
    val nodes: List<Node>,
) {
    internal sealed interface Node {
        val transform: Matrix?

        /** The layer this node composites through, or null when it draws straight to the canvas. */
        val layer: Paint?
    }

    internal class Group(
        override val transform: Matrix?,
        override val layer: Paint?,
        val children: List<Node>,
    ) : Node

    internal class Shape(
        override val transform: Matrix?,
        override val layer: Paint?,
        val path: Path,
        val fill: Color?,
        val stroke: StrokePaint?,
    ) : Node

    internal class StrokePaint(val color: Color, val style: Stroke)
}

internal fun diceBearScene(avatar: ResolvedAvatar): DiceBearScene = DiceBearScene(
    width = avatar.width,
    height = avatar.height,
    background = avatar.background?.let(::Color),
    nodes = avatar.nodes.map(::sceneNode),
)

private fun sceneNode(node: ResolvedNode): DiceBearScene.Node = when (node) {
    is ResolvedGroup -> DiceBearScene.Group(
        transform = node.transform?.toMatrix(),
        layer = node.layerPaint(),
        children = node.children.map(::sceneNode),
    )

    is ResolvedShape -> DiceBearScene.Shape(
        transform = node.transform?.toMatrix(),
        layer = node.layerPaint(),
        path = node.geometry.toPath().apply {
            fillType = if (node.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero
        },
        fill = node.fill?.toColor(),
        stroke = node.stroke?.let { stroke ->
            DiceBearScene.StrokePaint(
                color = stroke.paint.toColor(),
                style = Stroke(
                    width = stroke.width,
                    cap = stroke.cap.toCompose(),
                    join = stroke.join.toCompose(),
                ),
            )
        },
    )
}

/**
 * Draws [scene] into the current scope, scaled to its size.
 *
 * [ResolvedNode.opacity] and [ResolvedNode.blendMode] cannot be folded into the shapes they
 * cover: a group is composited as a whole, so they become a `saveLayer` instead. Several
 * styles depend on that - stripes and triangles overlay a translucent copy of their body on
 * itself, and rings lightens its white guides.
 */
internal fun DrawScope.drawDiceBear(scene: DiceBearScene) {
    withTransform({ scale(size.width / scene.width, size.height / scene.height, Offset.Zero) }) {
        // The reference always clips the body to the canvas - its border-radius clip at radius
        // 0 - and several styles deliberately draw past the edge.
        clipRect(0f, 0f, scene.width, scene.height) {
            val layerBounds = Rect(Offset.Zero, Size(scene.width, scene.height))
            scene.background?.let { drawRect(it, Offset.Zero, Size(scene.width, scene.height)) }
            scene.nodes.forEach { drawNode(it, layerBounds) }
        }
    }
}

private fun DrawScope.drawNode(node: DiceBearScene.Node, layerBounds: Rect) {
    val draw: DrawScope.() -> Unit = {
        node.layer?.let { drawContext.canvas.saveLayer(layerBounds, it) }
        when (node) {
            is DiceBearScene.Group -> node.children.forEach { drawNode(it, layerBounds) }
            is DiceBearScene.Shape -> {
                node.fill?.let { drawPath(node.path, it) }
                node.stroke?.let { drawPath(node.path, it.color, style = it.style) }
            }
        }
        node.layer?.let { drawContext.canvas.restore() }
    }
    val transform = node.transform
    if (transform != null) withTransform({ transform(transform) }) { draw() } else draw()
}

private fun ResolvedNode.layerPaint(): Paint? {
    if (opacity >= 1f && blendMode == null) return null
    val mode = blendMode
    return Paint().apply {
        alpha = opacity
        blendMode = when (mode) {
            null -> BlendMode.SrcOver
            ResolvedBlendMode.OVERLAY -> BlendMode.Overlay
            ResolvedBlendMode.LIGHTEN -> BlendMode.Lighten
        }
    }
}

/**
 * Maps the SVG affine transform onto Compose's 4x4 matrix, which stores the same values
 * column-major: `a b c d` land on ScaleX/SkewY/SkewX/ScaleY and `e f` on the translation.
 */
private fun AffineTransform.toMatrix(): Matrix = Matrix().apply {
    values[Matrix.ScaleX] = a
    values[Matrix.SkewX] = c
    values[Matrix.TranslateX] = e
    values[Matrix.SkewY] = b
    values[Matrix.ScaleY] = d
    values[Matrix.TranslateY] = f
}

private fun ResolvedGeometry.toPath(): Path = when (this) {
    is ResolvedGeometry.PathData -> PathParser().parsePathString(data).toPath()

    is ResolvedGeometry.Circle -> Path().apply {
        addOval(Rect(Offset(cx - radius, cy - radius), Size(radius * 2, radius * 2)))
    }

    is ResolvedGeometry.Ellipse -> Path().apply {
        addOval(Rect(Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2)))
    }

    is ResolvedGeometry.Rect -> {
        val bounds = Rect(Offset(x, y), Size(width, height))
        Path().apply {
            if (rx == 0f && ry == 0f) addRect(bounds) else addRoundRect(RoundRect(bounds, CornerRadius(rx, ry)))
        }
    }
}

private fun ResolvedPaint.toColor(): Color {
    val base = Color(color)
    return if (alpha >= 1f) base else base.copy(alpha = base.alpha * alpha)
}

private fun ResolvedStrokeCap.toCompose(): StrokeCap = when (this) {
    ResolvedStrokeCap.BUTT -> StrokeCap.Butt
    ResolvedStrokeCap.ROUND -> StrokeCap.Round
    ResolvedStrokeCap.SQUARE -> StrokeCap.Square
}

private fun ResolvedStrokeJoin.toCompose(): StrokeJoin = when (this) {
    ResolvedStrokeJoin.MITER -> StrokeJoin.Miter
    ResolvedStrokeJoin.ROUND -> StrokeJoin.Round
    ResolvedStrokeJoin.BEVEL -> StrokeJoin.Bevel
}
