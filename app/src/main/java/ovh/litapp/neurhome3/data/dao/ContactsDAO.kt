package ovh.litapp.neurhome3.data.dao

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.ContactsContract
import android.util.Size
import ovh.litapp.neurhome3.application.NeurhomeApplication
import ovh.litapp.neurhome3.data.Application


/** Bounded so a long contact list does not retain one bitmap per contact forever. */
private const val CONTACT_PHOTO_CACHE_SIZE = 64

/** Contact photos are only ever shown at avatar size, so decode a small thumbnail. */
private val CONTACT_PHOTO_SIZE = Size(128, 128)


class ContactsDAO(
    val application: NeurhomeApplication,
) {
    private val photoCache = object : LinkedHashMap<String, Drawable>(CONTACT_PHOTO_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Drawable>?): Boolean =
            size > CONTACT_PHOTO_CACHE_SIZE
    }

    fun getStarredContacts(): List<Application> {
        val queryUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.Contacts.EXTRA_ADDRESS_BOOK_INDEX, "true")
            .build()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone._ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
            ContactsContract.CommonDataKinds.Phone.IS_PRIMARY,
        )

        val selection = "${ContactsContract.CommonDataKinds.Phone.STARRED}='1' "

        val contacts = mutableListOf<Application>()

        application.contentResolver.query(
            queryUri, projection, selection, null, null
        )?.use { cur ->
            while (cur.moveToNext()) {
                val displayName = cur.getString(1)
                val photoUri = cur.getString(3)
                val phoneNumber = cur.getString(4)

                // DISPLAY_NAME_PRIMARY can be missing for restricted or aggregated rows and
                // NORMALIZED_NUMBER is nullable, so rows without both cannot drive the row.
                if (displayName.isNullOrBlank() || phoneNumber.isNullOrBlank()) continue

                val phoneIntent =
                    Intent(Intent.ACTION_CALL, Uri.fromParts("tel", phoneNumber, null))

                contacts.add(
                    Application(
                        label = displayName,
                        packageName = "com.google.android.dialer",
                        icon = loadContactPhoto(photoUri),
                        isVisible = true,
                        intent = phoneIntent
                    )
                )

            }

            return contacts.groupBy { it.label }.map { (_, apps) -> apps[0] }
        }

        return listOf()
    }

    /**
     * Resolves a contact thumbnail URI to a [Drawable] so the UI can render it with
     * `rememberDrawablePainter` (the same painter used for launcher icons). A missing, deleted, or
     * unreadable photo returns null, leaving the caller's person placeholder in place.
     */
    private fun loadContactPhoto(photoUri: String?): Drawable? {
        if (photoUri.isNullOrBlank()) return null
        synchronized(photoCache) { photoCache[photoUri] }?.let { return it }
        val photo = runCatching {
            val bitmap = application.contentResolver.loadThumbnail(
                Uri.parse(photoUri), CONTACT_PHOTO_SIZE, null
            )
            BitmapDrawable(application.resources, bitmap)
        }.getOrNull() ?: return null
        synchronized(photoCache) { photoCache[photoUri] = photo }
        return photo
    }
}
