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
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        )

        val selection = if (includeUnstarred) {
            null
        } else {
            "${ContactsContract.CommonDataKinds.Phone.STARRED}='1' "
        }

        val numbers = mutableListOf<ContactNumber>()

        application.contentResolver.query(
            queryUri, projection, selection, null, null
        )?.use { cur ->
            while (cur.moveToNext()) {
                val contactId = if (cur.isNull(9)) null else cur.getLong(9)
                val displayName = cur.getString(1)
                val photoUri = cur.getString(3)
                val normalizedNumber = cur.getString(4)
                val rawNumber = cur.getString(6)
                val type = if (cur.isNull(7)) null else cur.getInt(7)
                val typeLabel = cur.getString(8)

                // The provider normalizes the number it stores, but a row inserted by another tool
                // (or by a provider that skips it) can leave NORMALIZED_NUMBER null. Falling back to
                // the formatted number keeps that contact callable instead of dropping it; the URI
                // is normalized locally so the identity stays a bare `tel:` number either way.
                val phoneNumber = normalizedNumber?.takeIf { it.isNotBlank() }
                    ?: rawNumber?.takeIf { it.isNotBlank() }?.let(PhoneNumberUtils::normalizeNumber)
                if (phoneNumber.isNullOrBlank()) continue

                // The number as the provider displays it, which is what a row without a name reads
                // as. DISPLAY_NAME_PRIMARY is missing for a contact saved with only a number, and
                // such a row is still a callable row: it is shown as its number rather than
                // dropped, and it gets no type suffix (see [contactLabel]).
                val displayedNumber = rawNumber?.takeIf { it.isNotBlank() } ?: phoneNumber

                // The row's name as the provider spells it, and the same name only when it really
                // is a name. Both answers are computed here, once, so the label's type suffix and
                // the avatar seed cannot disagree about whether this row has a name at all.
                val shownName = displayName?.trim()?.takeIf { it.isNotEmpty() }
                val contactName = shownName?.takeUnless { isTheNumber(it, displayedNumber, phoneNumber) }

                numbers.add(
                    ContactNumber(
                        contactId = contactId,
                        displayedNumber = displayedNumber,
                        phoneNumber = phoneNumber,
                        photoUri = photoUri?.takeIf { it.isNotBlank() },
                        shownName = shownName,
                        name = contactName,
                        type = type,
                        typeLabel = typeLabel,
                    )
                )
            }

            // How many numbers each contact has, because the type suffix only earns its place on
            // a contact a reader has to tell apart from itself: a single-number contact keeps its
            // bare name. Distinct numbers, so a number the provider returns twice does not make
            // the contact look like it has two.
            val numbersPerContact = numbers
                .filter { it.contactId != null }
                .groupBy { it.contactId }
                .mapValues { (_, rows) -> rows.map { it.phoneNumber }.toSet().size }

            // One row per number, never per label: a contact's second number and two contacts
            // sharing a display name must each stay callable, and the call URI is exactly the
            // identity the launch log and the ranking resolve against.
            return numbers.distinctBy { it.phoneNumber }.map { row ->
                // A contact row is launched through an ACTION_CALL intent rather than a launcher
                // activity, so its call URI is its row identity: a launch of the row is logged
                // under exactly this string, which is what lets the ranking resolve the logged
                // key back to the row.
                val callUri = Uri.fromParts("tel", row.phoneNumber, null)
                Application(
                    label = contactLabel(
                        row,
                        suffixType = (numbersPerContact[row.contactId] ?: 0) > 1,
                    ),
                    packageName = callUri.toString(),
                    icon = null,
                    photoUri = row.photoUri,
                    intent = Intent(Intent.ACTION_CALL, callUri),
                    contactName = row.name,
                )
            }
        }

        return listOf()
    }

    /**
     * One number of one contact, before the [Application] row is built: the contact identity is
     * still needed to decide whether the row's type label is worth showing, and it is not part of
     * the row itself.
     */
    private data class ContactNumber(
        val contactId: Long?,
        val displayedNumber: String,
        val phoneNumber: String,
        val photoUri: String?,
        val shownName: String?,
        val name: String?,
        val type: Int?,
        val typeLabel: String?,
    )

    /**
     * The row label: [ContactNumber.name] - the contact's display name as the provider spells it -
     * or the row's number when the contact carries no name, suffixed with the number's type label
     * when the provider reports one **and** [suffixType] says the contact has several numbers to
     * tell apart. The type label comes from the platform (`getTypeLabel`), so it is localized and
     * a custom label is used verbatim instead of `"Other"`. A row whose provider leaves the type
     * empty keeps the bare name.
     *
     * The suffix is only added when the row actually reads as a name. A row that shows only a
     * phone number gets none: with no name in front of it the suffix attaches to the number and
     * reads as part of it (`+39 111 (Mobile)`), which claims something about the number that the
     * row is not saying. A provider is allowed to report the number itself as the display name for
     * a contact saved without one, so a name equal to the row's number counts as "no name" too -
     * which is why the caller decides that once, in [isTheNumber], and not here.
     */
    private fun contactLabel(row: ContactNumber, suffixType: Boolean): String {
        // A row with no real name shows whatever the provider called it - which, for a contact
        // saved with only a number, is that number.
        val name = row.name ?: return row.shownName ?: row.displayedNumber
        val type = row.type
        if (!suffixType || type == null) return name

        val typeName = ContactsContract.CommonDataKinds.Phone
            .getTypeLabel(application.resources, type, row.typeLabel)
            .toString()
            .trim()

        return if (typeName.isEmpty()) name else "$name ($typeName)"
    }

    /**
     * Whether [name] is the row's own number rather than a name, which the platform decides
     * loosely: `PhoneNumberUtils.compare` ignores separators and a country code that one side
     * spells out and the other stores as a default prefix.
     *
     * The deprecated `Context` overload is deliberate: it is the only one that resolves the
     * default country from the device (SIM, then network) without this class having to read a
     * `TelephonyManager` itself, which would need a permission the app does not hold.
     */
    @Suppress("DEPRECATION")
    private fun isTheNumber(name: String, displayedNumber: String, phoneNumber: String): Boolean =
        listOf(displayedNumber, phoneNumber)
            .filter { it.isNotBlank() }
            .any { PhoneNumberUtils.compare(application, name, it) }
}
