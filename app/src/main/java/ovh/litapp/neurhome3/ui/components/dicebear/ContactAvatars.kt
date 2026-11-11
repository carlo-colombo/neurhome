package ovh.litapp.neurhome3.ui.components.dicebear

import android.content.Context
import ovh.litapp.neurhome3.data.avatar.DiceBearStyle

/** Bounded so a long contact list does not retain a parsed scene per contact forever. */
private const val AVATAR_CACHE_SIZE = 64

/**
 * The process-wide cache of resolved avatar scenes, keyed by style and seed.
 *
 * Resolving a style is a JSON-driven PRNG walk that also parses every path the chosen variants
 * draw - dozens per variant body - so the same work must not repeat each time a row scrolls back
 * into the composition. A [DiceBearScene] is immutable once built and [drawDiceBear] scales it to
 * the space it is drawn in, so one scene serves every draw at any avatar size.
 *
 * It is held by `NeurhomeApplication` because it outlives every screen that draws a contact row:
 * the merged lists, the favourites bar and the statistics list all show the same rows.
 */
internal class ContactAvatars(private val context: Context) {

    private data class Key(val style: DiceBearStyle, val seed: String)

    /** Access-ordered, so the entry evicted at capacity is the one drawn longest ago. */
    private val cache = object : LinkedHashMap<Key, DiceBearScene>(AVATAR_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, DiceBearScene>?): Boolean =
            size > AVATAR_CACHE_SIZE
    }

    /** The scene this cache already holds for [style] and [seed], or null. Never resolves. */
    fun cached(seed: String, style: DiceBearStyle): DiceBearScene? =
        synchronized(cache) { cache[Key(style, seed)] }

    /**
     * The scene for [style] and [seed], resolved and cached the first time it is asked for.
     *
     * A [DiceBearStyle.NONE] row resolves to null and takes no cache entry: a style that draws
     * nothing must not hold a slot per contact.
     */
    fun scene(seed: String, style: DiceBearStyle): DiceBearScene? {
        cached(seed, style)?.let { return it }
        val scene = style.resolve(context, seed)?.let(::diceBearScene) ?: return null
        synchronized(cache) { cache[Key(style, seed)] = scene }
        return scene
    }
}
