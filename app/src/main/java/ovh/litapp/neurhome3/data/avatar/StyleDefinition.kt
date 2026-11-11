package ovh.litapp.neurhome3.data.avatar

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * The subset of a DiceBear v10 style definition (`@dicebear/schema` 1.4.0) that this app
 * reads.
 *
 * A style is JSON describing a scene graph, not SVG text: the only textual drawing in it
 * is the path `d` string. The vendored definitions live in `assets/dicebear/`.
 */
@Serializable
data class StyleDefinition(
    val meta: StyleMeta = StyleMeta(),
    val canvas: StyleCanvas,
    val attributes: Map<String, StyleAttributeValue> = emptyMap(),
    val components: Map<String, StyleComponent> = emptyMap(),
    val colors: Map<String, StyleColor> = emptyMap(),
)

@Serializable
data class StyleMeta(
    val license: StyleLicense? = null,
    val creator: StyleCredit? = null,
    val source: StyleCredit? = null,
)

/** A style's license, including the attribution line its author asks to be reproduced. */
@Serializable
data class StyleLicense(
    val name: String? = null,
    val url: String? = null,
    val text: String? = null,
)

@Serializable
data class StyleCredit(
    val name: String? = null,
    val url: String? = null,
)

@Serializable
data class StyleCanvas(
    val width: Double,
    val height: Double,
    val elements: List<StyleElement> = emptyList(),
)

@Serializable
data class StyleElement(
    val type: StyleElementType,
    val name: String? = null,
    /** The content of a [StyleElementType.TEXT] element. */
    val value: String? = null,
    val attributes: Map<String, StyleAttributeValue> = emptyMap(),
    val children: List<StyleElement> = emptyList(),
)

/**
 * An unknown element type fails parsing rather than being skipped: a style that grew a
 * construct this renderer does not implement must be reported, not silently drawn wrong.
 */
@Serializable
enum class StyleElementType {
    @SerialName("element")
    ELEMENT,

    @SerialName("component")
    COMPONENT,

    @SerialName("text")
    TEXT,
}

/**
 * One attribute value: a literal string (`"translate(0 4)"`, `"#1499da"`, `"none"`), a
 * reference to a resolved colour group, or a reference to a variable this renderer does
 * not resolve (`initials`, `fontWeight`, ...).
 */
@Serializable(with = StyleAttributeValueSerializer::class)
sealed interface StyleAttributeValue {
    data class Literal(val value: String) : StyleAttributeValue

    data class ColorReference(val name: String) : StyleAttributeValue

    data class VariableReference(val name: String) : StyleAttributeValue
}

internal object StyleAttributeValueSerializer : KSerializer<StyleAttributeValue> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("ovh.litapp.neurhome3.data.avatar.StyleAttributeValue")

    override fun deserialize(decoder: Decoder): StyleAttributeValue {
        val input = decoder as? JsonDecoder
            ?: throw SerializationException("style definitions are JSON")
        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> StyleAttributeValue.Literal(element.content)
            is JsonObject -> {
                val name = element["name"]?.jsonPrimitive?.content
                    ?: throw SerializationException("attribute reference without a name: $element")
                when (val type = element["type"]?.jsonPrimitive?.content) {
                    "color" -> StyleAttributeValue.ColorReference(name)
                    "variable" -> StyleAttributeValue.VariableReference(name)
                    else -> throw SerializationException("unsupported attribute reference type '$type'")
                }
            }

            else -> throw SerializationException("unsupported attribute value: $element")
        }
    }

    override fun serialize(encoder: Encoder, value: StyleAttributeValue) {
        throw UnsupportedOperationException("style definitions are read-only")
    }
}

/**
 * A component is either a base component with variants, or an alias declared through
 * [extends] that inherits its source's dimensions, variants and transforms.
 */
@Serializable
data class StyleComponent(
    val extends: String? = null,
    val width: Double? = null,
    val height: Double? = null,
    /** The chance (0-100) that this component is drawn at all; 100 when unset. */
    val probability: Double? = null,
    val rotate: StyleRange? = null,
    val scale: StyleRange? = null,
    val translate: StyleTranslate? = null,
    val variants: Map<String, StyleVariant> = emptyMap(),
)

/** A `{ min, max, step }` sampling range. */
@Serializable
data class StyleRange(
    val min: Double,
    val max: Double,
    val step: Double? = null,
)

/** Per-component translation, expressed as a percentage of the component's own size. */
@Serializable
data class StyleTranslate(
    val x: StyleRange? = null,
    val y: StyleRange? = null,
)

@Serializable
data class StyleVariant(
    val weight: Double? = null,
    val elements: List<StyleElement> = emptyList(),
)

/**
 * A colour group: the palette its winner is drawn from, plus the best-effort constraints
 * that keep it legible against other groups.
 */
@Serializable
data class StyleColor(
    val values: List<String> = emptyList(),
    /** Sort this group's candidates by contrast against this group, best first. */
    val contrastTo: String? = null,
    /** Drop candidates equal to any colour already resolved for these groups. */
    val notEqualTo: List<String> = emptyList(),
)

/**
 * Scoped to the style definitions: their JSON carries keys (`$id`, `$schema`, `$comment`)
 * that this model deliberately does not mirror, and nothing else in the app parses
 * untrusted JSON this way.
 */
private val styleJson = Json { ignoreUnknownKeys = true }

fun parseStyleDefinition(json: String): StyleDefinition = styleJson.decodeFromString(json)
