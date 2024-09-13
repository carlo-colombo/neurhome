package ovh.litapp.neurhome3.data.dao

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import ovh.litapp.neurhome3.NeurhomeApplication
import ovh.litapp.neurhome3.data.Application


private const val TAG = "ContactsDAO"


class ContactsDAO(
    val application: NeurhomeApplication,
) {
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
                val isPrimary = cur.getString(5)

                Log.d(TAG, "$displayName, $phoneNumber, $isPrimary")

                val phoneIntent =
                    Intent(Intent.ACTION_CALL, Uri.fromParts("tel", phoneNumber, null))

                contacts.add(
                    Application(
                        label = displayName,
                        packageName = "com.google.android.dialer",
                        icon = photoUri,
                        isVisible = true,
                        intent = phoneIntent
                    )
                )

            }

            return contacts.groupBy { it.label }.map { (_, apps) -> apps[0] }
        }

        return listOf()
    }


}