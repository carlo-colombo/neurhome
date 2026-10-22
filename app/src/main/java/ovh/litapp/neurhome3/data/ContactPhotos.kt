package ovh.litapp.neurhome3.data

import android.content.ContentResolver
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Size
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ContactPhotos"

/** Bounded so a long contact list does not retain one bitmap per contact forever. */
private const val PHOTO_CACHE_SIZE = 64

/** Contact photos are only ever shown at avatar size, so decode a small thumbnail. */
private val PHOTO_SIZE = Size(128, 128)

/**
 * The process-wide contact-thumbnail cache.
 *
 * Contact rows carry a `content://` thumbnail URI and never a decoded drawable, because a full
 * address book would otherwise decode one bitmap per contact on every list refresh. The UI asks for
 * a photo only for the rows it composes, and this class decodes that one request off the main
 * thread and keeps the result in a bounded LRU.
 */
class ContactPhotos(
    private val contentResolver: ContentResolver,
    private val resources: Resources,
) {
    private val cache = object : LinkedHashMap<String, Drawable>(PHOTO_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Drawable>?): Boolean =
            size > PHOTO_CACHE_SIZE
    }

    /** One-shot so a resolver that fails for every contact cannot log once per row. */
    @Volatile
    private var thumbnailFailureLogged = false

    /** The already decoded photo for [uri], if this cache still holds it. Never touches disk. */
    fun cached(uri: String): Drawable? = synchronized(cache) { cache[uri] }

    /**
     * Resolves a contact thumbnail URI to a [Drawable] so the UI can render it with
     * `rememberDrawablePainter` (the same painter used for launcher icons). A missing, deleted, or
     * unreadable photo returns null, leaving the caller's person placeholder in place.
     */
    suspend fun load(uri: String): Drawable? = withContext(Dispatchers.IO) {
        cached(uri) ?: decode(uri)
    }

    private fun decode(uri: String): Drawable? {
        val photo = runCatching {
            val bitmap = contentResolver.loadThumbnail(uri.toUri(), PHOTO_SIZE, null)
            bitmap.toDrawable(resources)
        }.onFailure { error ->
            if (!thumbnailFailureLogged) {
                thumbnailFailureLogged = true
                Log.w(TAG, "Could not load a contact thumbnail; using placeholders", error)
            }
        }.getOrNull() ?: return null
        synchronized(cache) { cache[uri] = photo }
        return photo
    }
}
