package ovh.litapp.neurhome3.data.dao

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import ovh.litapp.neurhome3.application.NeurhomeApplication
import ovh.litapp.neurhome3.data.Application


class ContactsDAO(
    val application: NeurhomeApplication,
) {

    /**
     * The callable contact rows, one per phone number. [includeUnstarred] selects the whole address
     * book instead of the starred subset; the caller owns that choice through the persisted
     * `contacts.call.candidates` setting.
     *
     * The rows carry their thumbnail URI instead of a decoded drawable: a full address book makes
     * eager decoding absurd, so the UI resolves a photo only when it actually shows the row.
     */
    fun getContacts(includeUnstarred: Boolean): List<Application> {
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
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
        )

        val selection = if (includeUnstarred) {
            null
        } else {
            "${ContactsContract.CommonDataKinds.Phone.STARRED}='1' "
        }

        val contacts = mutableListOf<Application>()

        application.contentResolver.query(
            queryUri, projection, selection, null, null
        )?.use { cur ->
            while (cur.moveToNext()) {
                val displayName = cur.getString(1)
                val photoUri = cur.getString(3)
                val normalizedNumber = cur.getString(4)
                val rawNumber = cur.getString(6)
                val type = if (cur.isNull(7)) null else cur.getInt(7)
                val typeLabel = cur.getString(8)

                // DISPLAY_NAME_PRIMARY can be missing for restricted or aggregated rows, and a
                // row's number can be missing entirely, so rows without both cannot drive the row.
                if (displayName.isNullOrBlank()) continue

                // The provider normalizes the number it stores, but a row inserted by another tool
                // (or by a provider that skips it) can leave NORMALIZED_NUMBER null. Falling back to
                // the formatted number keeps that contact callable instead of dropping it; the URI
                // is normalized locally so the identity stays a bare `tel:` number either way.
                val phoneNumber = normalizedNumber?.takeIf { it.isNotBlank() }
                    ?: rawNumber?.takeIf { it.isNotBlank() }?.let(PhoneNumberUtils::normalizeNumber)
                if (phoneNumber.isNullOrBlank()) continue

                // A contact row is launched through an ACTION_CALL intent rather than a launcher
                // activity, so its call URI is its row identity: a launch of the row is logged
                // under exactly this string, which is what lets the ranking resolve the logged
                // key back to the row.
                val callUri = Uri.fromParts("tel", phoneNumber, null)

                contacts.add(
                    Application(
                        // A contact with several numbers yields several rows that would otherwise
                        // read identically, so the row label carries the number's own type label
                        // ("Mobile", "Home", "Work", a custom label, ...) whenever the provider
                        // supplies one.
                        label = contactLabel(displayName, type, typeLabel),
                        packageName = callUri.toString(),
                        icon = null,
                        photoUri = photoUri?.takeIf { it.isNotBlank() },
                        intent = Intent(Intent.ACTION_CALL, callUri)
                    )
                )

            }

            // One row per number, never per label: a contact's second number and two contacts
            // sharing a display name must each stay callable, and the call URI is exactly the
            // identity the launch log and the ranking resolve against.
            return contacts.distinctBy { it.packageName }
        }

        return listOf()
    }

    /**
     * The row label: the display name, suffixed with the phone number's type label when the
     * provider reports one for the row's number. The type label comes from the platform
     * (`getTypeLabel`), so it is localized and a custom label is used verbatim instead of
     * `"Other"`. A row whose provider leaves the type empty keeps the bare display name.
     */
    private fun contactLabel(displayName: String, type: Int?, typeLabel: String?): String {
        if (type == null) return displayName

        val typeName = ContactsContract.CommonDataKinds.Phone
            .getTypeLabel(application.resources, type, typeLabel)
            .toString()
            .trim()

        return if (typeName.isEmpty()) displayName else "$displayName ($typeName)"
    }
}
