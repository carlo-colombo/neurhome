package ovh.litapp.neurhome3.data.avatar

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * The DiceBear styles vendored in `assets/dicebear/`, plus [NONE] for "no generated avatar".
 *
 * The definitions are unmodified `@dicebear/styles` 10.6.0 output (all CC0 1.0; their
 * attribution lives in `assets/dicebear/LICENSES.md`), and drawing them on the device is
 * what keeps a contact's avatar offline and out of a third party's request log.
 */
enum class DiceBearStyle(val id: String, val assetPath: String?) {
    IDENTICON("identicon", "dicebear/identicon.json"),
    STRIPES("stripes", "dicebear/stripes.json"),
    TRIANGLES("triangles", "dicebear/triangles.json"),
    WEAVE("weave", "dicebear/weave.json"),
    RINGS("rings", "dicebear/rings.json"),
    SHAPE_GRID("shape-grid", "dicebear/shape-grid.json"),
    LINE_FACE("line-face", "dicebear/line-face.json"),
    SHADOWS("shadows", "dicebear/shadows.json"),
    SHAPES("shapes", "dicebear/shapes.json"),
    LORELEI_NEUTRAL("lorelei-neutral", "dicebear/lorelei-neutral.json"),
    NONE("none", null);

    /**
     * The parsed definition, or null for [NONE].
     *
     * Parsed once per style and cached for the process: a definition is immutable, and the
     * parse is on the order of a few milliseconds, which must not repeat per draw.
     */
    fun definition(context: Context): StyleDefinition? {
        val path = assetPath ?: return null
        definitions[this]?.let { return it }
        return definitions.computeIfAbsent(this) {
            parseStyleDefinition(context.assets.open(path).bufferedReader().use { reader -> reader.readText() })
        }
    }

    /** The attribution line the style's author asks to be reproduced. */
    fun licenseText(context: Context): String? = definition(context)?.meta?.license?.text

    /** The draw tree for [seed], or null for [NONE]. */
    fun resolve(context: Context, seed: String): ResolvedAvatar? =
        definition(context)?.let { AvatarStyleResolver(it).resolve(seed) }

    companion object {
        /**
         * The style the app starts with: a clean install, and any stored value this build does not
         * know, draws [SHADOWS].
         */
        val DEFAULT = SHADOWS

        /** Reads a style back from its stored enum name; blank or unknown values are [DEFAULT]. */
        fun fromStoredValue(value: String): DiceBearStyle =
            entries.firstOrNull { it.name == value } ?: DEFAULT

        private val definitions = ConcurrentHashMap<DiceBearStyle, StyleDefinition>()
    }
}
