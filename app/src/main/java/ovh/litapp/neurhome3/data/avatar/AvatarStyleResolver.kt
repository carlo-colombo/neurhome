package ovh.litapp.neurhome3.data.avatar

import kotlin.math.pow

/**
 * Thrown when a style definition uses a construct this renderer does not implement.
 *
 * A style that grew a mask, a gradient or a text element must fail loudly here instead of
 * being drawn with that part missing, because a silently dropped mask or gradient is a
 * visibly wrong avatar rather than a small one.
 */
class UnsupportedStyleFeatureException(message: String) : IllegalArgumentException(message)

/**
 * Resolves one [StyleDefinition] for a seed: which component variants are drawn, which
 * palette colour each colour group wins, and the resulting draw tree.
 *
 * The selection rules follow the reference implementation exactly
 * (<https://www.dicebear.com/specification/implement-dicebear-core/>), because the point of
 * drawing on the device is that a contact's avatar matches the one the same seed produces
 * everywhere else. `tools/dicebear_parity_fixture.py` is the reference this was verified
 * against; `DiceBearParityTest` pins it.
 *
 * Only the option subset the app exposes is resolved: the seed, and the style's own
 * defaults. Size, title, flip, rotate, scale, borderRadius, translate and the user colour
 * and variant overrides are not implemented because the app never sets them.
 */
class AvatarStyleResolver(private val definition: StyleDefinition) {

    /** The PRNG-derived choices for [seed], in the reference's vocabulary. */
    fun resolveOptions(seed: String): ResolvedStyleOptions =
        Resolution(definition, DiceBearPrng(seed)).options()

    /** The draw tree for [seed]. */
    fun resolve(seed: String): ResolvedAvatar {
        val resolution = Resolution(definition, DiceBearPrng(seed))
        val options = resolution.options()
        return ResolvedAvatar(
            width = definition.canvas.width.toFloat(),
            height = definition.canvas.height.toFloat(),
            background = options.colors[BACKGROUND]?.firstOrNull()?.let { parseHexColor(it, "background") },
            nodes = resolution.nodes(definition.canvas.elements, resolution.canvasPaintState()),
        )
    }
}

private const val BACKGROUND = "background"

private val PAINT_ATTRIBUTES = setOf(
    "fill",
    "fill-opacity",
    "fill-rule",
    // Only meaningful for a clip path, and the vendored styles clip nothing.
    "clip-rule",
    "stroke",
    "stroke-opacity",
    "stroke-width",
    "stroke-linecap",
    "stroke-linejoin",
)

private val LAYER_ATTRIBUTES = setOf("opacity", "style", "transform", "class")

private val GEOMETRY_ATTRIBUTES = setOf("d", "cx", "cy", "r", "rx", "ry", "width", "height", "x", "y")

private class Resolution(
    private val definition: StyleDefinition,
    private val prng: DiceBearPrng,
) {
    private val colors = ColorResolution(definition, prng)
    private val components = mutableMapOf<String, ResolvedComponent>()

    fun options(): ResolvedStyleOptions = ResolvedStyleOptions(
        components = definition.components.keys.associateWith { component(it).options },
        colors = colors.resolveAll(),
    )

    /**
     * The paint state the canvas itself establishes. Every vendored definition sets
     * `fill="none"` here, which is what keeps its stroke-only shapes from being filled
     * black; `shape-rendering` is a rasterisation hint with no geometry effect and is
     * ignored.
     */
    fun canvasPaintState(): PaintState = PaintState.DEFAULT.with(definition.attributes.filterKeys {
        it in PAINT_ATTRIBUTES
    }, ::colorOf)

    fun nodes(elements: List<StyleElement>, state: PaintState): List<ResolvedNode> =
        elements.mapNotNull { node(it, state) }

    private fun node(element: StyleElement, state: PaintState): ResolvedNode? = when (element.type) {
        StyleElementType.ELEMENT -> elementNode(element, state)
        StyleElementType.COMPONENT -> componentNode(element, state)
        // Text is only ever the content of a <style> element, which elementNode drops whole.
        StyleElementType.TEXT -> throw UnsupportedStyleFeatureException(
            "text content is not supported; the vendored styles only use it for animation CSS",
        )
    }

    private fun elementNode(element: StyleElement, state: PaintState): ResolvedNode? {
        val name = element.name
            ?: throw UnsupportedStyleFeatureException("an element without a name")
        // The only <style> elements in the vendored styles carry @keyframes CSS for the
        // animated variants, none of which is ever selected. Nothing about them is drawable.
        if (name == "style") return null
        val allowed = if (name == "g") {
            PAINT_ATTRIBUTES + LAYER_ATTRIBUTES
        } else {
            GEOMETRY_ATTRIBUTES + PAINT_ATTRIBUTES + LAYER_ATTRIBUTES
        }
        rejectUnknownAttributes(element, allowed)
        val transform = transformOf(element.attributes)
        val opacity = element.attributes.float("opacity") ?: 1f
        val blendMode = blendModeOf(element.attributes["style"])
        if (name == "g") {
            val children = nodes(element.children, state.with(element.attributes, ::colorOf))
            return ResolvedGroup(transform, opacity, blendMode, children)
        }
        if (element.children.isNotEmpty()) {
            throw UnsupportedStyleFeatureException("<$name> with children is not supported")
        }
        val paint = state.with(element.attributes, ::colorOf)
        return ResolvedShape(
            transform = transform,
            opacity = opacity,
            blendMode = blendMode,
            geometry = geometryOf(name, element.attributes),
            fill = paint.fill?.let { ResolvedPaint(it, paint.fillAlpha) },
            stroke = paint.stroke?.let {
                ResolvedStroke(ResolvedPaint(it, paint.strokeAlpha), paint.strokeWidth, paint.cap, paint.join)
            },
            evenOdd = paint.evenOdd,
        )
    }

    /**
     * A component reference is drawn as the variant's body inside the reference's own
     * transform, with the per-component transform (sampled from the component's `rotate`,
     * `translate` and `scale` ranges) applied inside it.
     */
    private fun componentNode(element: StyleElement, state: PaintState): ResolvedNode? {
        val name = element.name
            ?: throw UnsupportedStyleFeatureException("a component reference without a name")
        rejectUnknownAttributes(element, LAYER_ATTRIBUTES)
        val component = component(name)
        val variant = component.variant ?: return null
        val author = transformOf(element.attributes) ?: AffineTransform.IDENTITY
        val transform = (author * (component.transform ?: AffineTransform.IDENTITY))
            .takeUnless { it == AffineTransform.IDENTITY }
        return ResolvedGroup(
            transform = transform,
            opacity = element.attributes.float("opacity") ?: 1f,
            blendMode = blendModeOf(element.attributes["style"]),
            // A <use> adds no paint of its own, so its body inherits the state at the call site.
            children = nodes(variant.elements, state),
        )
    }

    private fun geometryOf(name: String, attributes: Map<String, StyleAttributeValue>): ResolvedGeometry =
        when (name) {
            "path" -> ResolvedGeometry.PathData(
                attributes.literal("d")
                    ?: throw UnsupportedStyleFeatureException("<path> without a 'd' attribute"),
            )

            "circle" -> ResolvedGeometry.Circle(
                cx = attributes.float("cx") ?: 0f,
                cy = attributes.float("cy") ?: 0f,
                radius = attributes.float("r") ?: 0f,
            )

            "ellipse" -> ResolvedGeometry.Ellipse(
                cx = attributes.float("cx") ?: 0f,
                cy = attributes.float("cy") ?: 0f,
                rx = attributes.float("rx") ?: 0f,
                ry = attributes.float("ry") ?: 0f,
            )

            "rect" -> {
                val rx = attributes.float("rx") ?: 0f
                ResolvedGeometry.Rect(
                    x = attributes.float("x") ?: 0f,
                    y = attributes.float("y") ?: 0f,
                    width = attributes.float("width") ?: 0f,
                    height = attributes.float("height") ?: 0f,
                    rx = rx,
                    // SVG: a missing ry takes the value of rx.
                    ry = attributes.float("ry") ?: rx,
                )
            }

            else -> throw UnsupportedStyleFeatureException("unsupported element <$name>")
        }

    private fun component(name: String): ResolvedComponent = components.getOrPut(name) {
        val alias = definition.components[name]
            ?: throw UnsupportedStyleFeatureException("reference to unknown component '$name'")
        val sourceName = alias.extends ?: name
        val source = definition.components[sourceName]
            ?: throw UnsupportedStyleFeatureException("component '$name' extends unknown '$sourceName'")
        if (source.extends != null) {
            throw UnsupportedStyleFeatureException("alias chains are not supported ('$name' -> '$sourceName')")
        }
        // The user-facing options come from the source component, but every PRNG key uses
        // the alias's own name - that is how identicon's row01..row05 roll independently.
        val visible = prng.bool("${name}Probability", source.probability ?: 100.0)
        val variantName = if (visible) {
            prng.weightedPick(
                "${name}Variant",
                source.variants.mapValues { (_, variant) -> variant.weight ?: 1.0 },
            )
        } else {
            null
        }
        val options = ResolvedComponentOptions(
            visible = visible,
            variant = variantName,
            rotate = prng.float("${name}Rotate", source.rotate) ?: 0.0,
            translateX = prng.float("${name}TranslateX", source.translate?.x) ?: 0.0,
            translateY = prng.float("${name}TranslateY", source.translate?.y) ?: 0.0,
            scale = prng.float("${name}Scale", source.scale) ?: 1.0,
        )
        ResolvedComponent(
            variant = variantName?.let { source.variants[it] },
            transform = placementTransform(source, options),
            options = options,
        )
    }

    /** The per-component transform the reference emits on the `<use>` for this component. */
    private fun placementTransform(source: StyleComponent, options: ResolvedComponentOptions): AffineTransform? {
        val width = (source.width ?: 0.0).toFloat()
        val height = (source.height ?: 0.0).toFloat()
        var transform = AffineTransform.IDENTITY
        if (options.translateX != 0.0 || options.translateY != 0.0) {
            // Percentages of the component's own size, not of the canvas.
            transform *= AffineTransform.translate(
                (options.translateX / 100 * width).toFloat(),
                (options.translateY / 100 * height).toFloat(),
            )
        }
        if (options.rotate != 0.0) {
            transform *= AffineTransform.rotate(options.rotate.toFloat(), width / 2, height / 2)
        }
        if (options.scale != 1.0) {
            val scale = options.scale.toFloat()
            transform *= AffineTransform.translate(width / 2, height / 2) *
                AffineTransform.scale(scale, scale) *
                AffineTransform.translate(-width / 2, -height / 2)
        }
        return transform.takeUnless { it == AffineTransform.IDENTITY }
    }

    private fun transformOf(attributes: Map<String, StyleAttributeValue>): AffineTransform? =
        attributes.literal("transform")?.let(::parseTransformList)

    /**
     * Reads `style="mix-blend-mode:overlay"`. Other CSS declarations are ignored: the
     * vendored definitions carry no other declaration, which the definition test pins.
     */
    private fun blendModeOf(style: StyleAttributeValue?): ResolvedBlendMode? {
        val value = style?.literal("style") ?: return null
        for (declaration in value.split(';')) {
            val parts = declaration.split(':', limit = 2)
            if (parts.size != 2 || parts[0].trim() != "mix-blend-mode") continue
            return when (val mode = parts[1].trim()) {
                "overlay" -> ResolvedBlendMode.OVERLAY
                "lighten" -> ResolvedBlendMode.LIGHTEN
                else -> throw UnsupportedStyleFeatureException("unsupported mix-blend-mode '$mode'")
            }
        }
        return null
    }

    private fun colorOf(value: StyleAttributeValue, what: String): Int? = when (value) {
        is StyleAttributeValue.Literal -> when (val literal = value.value) {
            "none" -> null
            "white" -> 0xFFFFFFFF.toInt()
            "black" -> 0xFF000000.toInt()
            else -> parseHexColor(literal, what)
        }

        is StyleAttributeValue.ColorReference ->
            colors.resolve(value.name).firstOrNull()?.let { parseHexColor(it, what) }

        is StyleAttributeValue.VariableReference -> throw UnsupportedStyleFeatureException(
            "$what references the '${value.name}' variable, which is not supported",
        )
    }

    private fun rejectUnknownAttributes(element: StyleElement, allowed: Set<String>) {
        val unknown = element.attributes.keys - allowed
        if (unknown.isNotEmpty()) {
            throw UnsupportedStyleFeatureException(
                "<${element.name}> carries unsupported attribute(s) ${unknown.sorted()}",
            )
        }
    }
}

private class ResolvedComponent(
    val variant: StyleVariant?,
    val transform: AffineTransform?,
    val options: ResolvedComponentOptions,
)

/**
 * The inheritable paint properties of an SVG element, initialised from the canvas and
 * narrowed by each element on the way down.
 *
 * `opacity` is deliberately absent: it is not inherited in SVG, it applies to the element's
 * own group, which is why [ResolvedNode.opacity] carries it instead.
 */
private data class PaintState(
    val fill: Int?,
    val fillAlpha: Float,
    val evenOdd: Boolean,
    val stroke: Int?,
    val strokeAlpha: Float,
    val strokeWidth: Float,
    val cap: ResolvedStrokeCap,
    val join: ResolvedStrokeJoin,
) {
    fun with(
        attributes: Map<String, StyleAttributeValue>,
        color: (StyleAttributeValue, String) -> Int?,
    ): PaintState = copy(
        fill = if ("fill" in attributes) color(attributes.getValue("fill"), "fill") else fill,
        fillAlpha = attributes.float("fill-opacity") ?: fillAlpha,
        evenOdd = attributes.literal("fill-rule")?.let {
            when (it) {
                "evenodd" -> true
                "nonzero" -> false
                else -> throw UnsupportedStyleFeatureException("unsupported fill-rule '$it'")
            }
        } ?: evenOdd,
        stroke = if ("stroke" in attributes) color(attributes.getValue("stroke"), "stroke") else stroke,
        strokeAlpha = attributes.float("stroke-opacity") ?: strokeAlpha,
        strokeWidth = attributes.float("stroke-width") ?: strokeWidth,
        cap = attributes.literal("stroke-linecap")?.let {
            when (it) {
                "butt" -> ResolvedStrokeCap.BUTT
                "round" -> ResolvedStrokeCap.ROUND
                "square" -> ResolvedStrokeCap.SQUARE
                else -> throw UnsupportedStyleFeatureException("unsupported stroke-linecap '$it'")
            }
        } ?: cap,
        join = attributes.literal("stroke-linejoin")?.let {
            when (it) {
                "miter" -> ResolvedStrokeJoin.MITER
                "round" -> ResolvedStrokeJoin.ROUND
                "bevel" -> ResolvedStrokeJoin.BEVEL
                else -> throw UnsupportedStyleFeatureException("unsupported stroke-linejoin '$it'")
            }
        } ?: join,
    )

    companion object {
        /** SVG's initial values: filled black, stroked not at all. */
        val DEFAULT = PaintState(
            fill = 0xFF000000.toInt(),
            fillAlpha = 1f,
            evenOdd = false,
            stroke = null,
            strokeAlpha = 1f,
            strokeWidth = 1f,
            cap = ResolvedStrokeCap.BUTT,
            join = ResolvedStrokeJoin.MITER,
        )
    }
}

/**
 * Resolves every colour group: the winner is the front of a [DiceBearPrng.shuffle] of the
 * group's candidates - not a `pick` - after the definition's `contrastTo` and `notEqualTo`
 * constraints have been applied.
 */
private class ColorResolution(
    private val definition: StyleDefinition,
    private val prng: DiceBearPrng,
) {
    private val resolved = mutableMapOf<String, List<String>>()
    private val resolving = mutableSetOf<String>()

    fun resolveAll(): Map<String, List<String>> {
        // `background` is implicit: every style has the group even when it declares no palette.
        val groups = sortedSetOf(BACKGROUND).apply { addAll(definition.colors.keys) }
        return groups.associateWith { resolve(it) }
    }

    fun resolve(name: String): List<String> {
        resolved[name]?.let { return it }
        if (!resolving.add(name)) {
            throw UnsupportedStyleFeatureException("circular colour reference through '$name'")
        }
        try {
            val group = definition.colors[name]
            if (group == null) {
                // No palette and no user-supplied colours: this group paints nothing.
                resolved[name] = emptyList()
                return emptyList()
            }
            var candidates = group.values.map(::normalizeHex)
            val contrastTo = group.contrastTo
            if (contrastTo != null) {
                resolve(contrastTo).firstOrNull()?.let { reference ->
                    candidates = sortByContrast(candidates, reference)
                }
            }
            if (group.notEqualTo.isNotEmpty()) {
                candidates = filterNotEqualTo(candidates, group.notEqualTo.flatMap { resolve(it) })
            }
            // A contrast sort must survive; everything else is shuffled into a random order.
            val ordered = if (contrastTo != null) candidates else prng.shuffle("${name}Color", candidates)
            // A solid fill, so the group contributes exactly one colour.
            return ordered.take(1).also { resolved[name] = it }
        } finally {
            resolving.remove(name)
        }
    }
}

/** Lowercase hex with the `#` restored and 3-/4-digit shorthand expanded, as the reference does. */
internal fun normalizeHex(value: String): String {
    val digits = value.removePrefix("#").lowercase()
    val expanded = if (digits.length == 3 || digits.length == 4) {
        digits.map { "$it$it" }.joinToString("")
    } else {
        digits
    }
    return "#$expanded"
}

/** `#rgb`, `#rgba`, `#rrggbb` or `#rrggbbaa` to packed ARGB. */
internal fun parseHexColor(value: String, what: String): Int {
    val digits = normalizeHex(value).removePrefix("#")
    if (digits.length != 6 && digits.length != 8) {
        throw UnsupportedStyleFeatureException("$what: '$value' is not a hex colour")
    }
    fun channel(start: Int): Int = digits.substring(start, start + 2).toIntOrNull(16)
        ?: throw UnsupportedStyleFeatureException("$what: '$value' is not a hex colour")
    val alpha = if (digits.length == 8) channel(6) else 0xFF
    return (alpha shl 24) or (channel(0) shl 16) or (channel(2) shl 8) or channel(4)
}

/**
 * Orders [candidates] by descending WCAG 2.1 contrast against [reference], stably.
 *
 * The reference implementation embeds a 256-entry linearisation table because `pow` is not
 * correctly rounded and it needs bit-identical doubles across languages. Here the value only
 * orders candidates, and the vendored palettes are black-and-white against a pastel, which
 * no last-bit difference can reorder - so the formula is used directly.
 */
internal fun sortByContrast(candidates: List<String>, reference: String): List<String> {
    val referenceLuminance = luminance(reference)
    return candidates.sortedByDescending { candidate ->
        val candidateLuminance = luminance(candidate)
        (maxOf(candidateLuminance, referenceLuminance) + 0.05) /
            (minOf(candidateLuminance, referenceLuminance) + 0.05)
    }
}

/** Drops candidates equal to an excluded colour, falling back to all of them rather than to none. */
internal fun filterNotEqualTo(candidates: List<String>, excluded: List<String>): List<String> {
    val blocked = excluded.map { normalizeHex(it).take(7) }.toSet()
    val filtered = candidates.filter { normalizeHex(it).take(7) !in blocked }
    return filtered.ifEmpty { candidates }
}

private fun luminance(hex: String): Double {
    val digits = normalizeHex(hex).removePrefix("#")

    fun linearize(channel: Int): Double {
        val s = channel / 255.0
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    return 0.2126 * linearize(digits.substring(0, 2).toInt(16)) +
        0.7152 * linearize(digits.substring(2, 4).toInt(16)) +
        0.0722 * linearize(digits.substring(4, 6).toInt(16))
}

/**
 * Parses an SVG transform list (`translate(0 4)`, `rotate(45 50 50)`, `matrix(a b c d e f)`,
 * `scale(2)`) into one affine transform. The vendored styles use only those four; `skewX`
 * and `skewY` are reported rather than guessed at.
 */
internal fun parseTransformList(value: String): AffineTransform {
    val text = value.trim()
    var transform = AffineTransform.IDENTITY
    var index = 0
    while (index < text.length) {
        val open = text.indexOf('(', index)
        val close = if (open < 0) -1 else text.indexOf(')', open)
        if (open < 0 || close < 0) {
            throw UnsupportedStyleFeatureException("malformed transform '$value'")
        }
        val function = text.substring(index, open).trim()
        val arguments = text.substring(open + 1, close)
            .split(',', ' ', '\t', '\n')
            .filter { it.isNotEmpty() }
            .map { it.toFloatOrNull() ?: throw UnsupportedStyleFeatureException("malformed transform '$value'") }
        transform *= when (function) {
            // A single argument means "translate x only" / "scale uniformly".
            "translate" -> AffineTransform.translate(
                tx = arguments.require(1..2, value)[0],
                ty = arguments.getOrNull(1) ?: 0f,
            )

            "scale" -> AffineTransform.scale(
                sx = arguments.require(1..2, value)[0],
                sy = arguments.getOrNull(1) ?: arguments[0],
            )

            "rotate" -> when (arguments.size) {
                1 -> AffineTransform.rotate(arguments[0])
                3 -> AffineTransform.rotate(arguments[0], arguments[1], arguments[2])
                else -> throw UnsupportedStyleFeatureException("malformed transform '$value'")
            }

            "matrix" -> {
                val values = arguments.require(6..6, value)
                AffineTransform(values[0], values[1], values[2], values[3], values[4], values[5])
            }

            else -> throw UnsupportedStyleFeatureException("unsupported transform function '$function'")
        }
        index = close + 1
    }
    return transform
}

private fun List<Float>.require(count: IntRange, value: String): List<Float> {
    if (size !in count) throw UnsupportedStyleFeatureException("malformed transform '$value'")
    return this
}

private fun Map<String, StyleAttributeValue>.literal(name: String): String? =
    (this[name] as? StyleAttributeValue.Literal)?.value

private fun StyleAttributeValue.literal(what: String): String =
    (this as? StyleAttributeValue.Literal)?.value
        ?: throw UnsupportedStyleFeatureException("$what must be a literal, not a reference")

private fun Map<String, StyleAttributeValue>.float(name: String): Float? =
    this[name]?.let { value ->
        val literal = value.literal(name)
        literal.toFloatOrNull() ?: throw UnsupportedStyleFeatureException("$name: '$literal' is not a number")
    }

private fun DiceBearPrng.float(key: String, range: StyleRange?): Double? =
    range?.let { float(key, it.min, it.max, it.step) }
