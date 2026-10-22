package ovh.litapp.neurhome3.data.dao

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ContentProviderController
import org.robolectric.annotation.Config
import ovh.litapp.neurhome3.application.NeurhomeApplication

/**
 * Drives [ContactsDAO.getContacts] against a seeded [MatrixCursor] served by a fake
 * `com.android.contacts` provider, so the read-by-index projection, the null/blank name-or-number
 * row guard (regression lock for BUG-03), the starred/all selections and the one-row-per-number
 * de-duplication run as production code without a device contact list.
 *
 * Photos are asserted only as carried URIs here: decoding is the UI's lazy step, covered by
 * `ContactPhotosTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ContactsDaoTest {
    private lateinit var application: NeurhomeApplication
    private lateinit var provider: FakeContactsProvider

    @Before
    fun setUp() {
        application = unstartedApplication()
        provider = ContentProviderController
            .of(FakeContactsProvider())
            .create(ContactsContract.AUTHORITY)
            .get()
    }

    @Test
    fun theStarredSelectionIsTheOnlyOnesReadWhenUnstarredContactsAreExcluded() {
        provider.rows = listOf(Row(id = 1, name = "Ada", number = "+39123"))

        val contacts = ContactsDAO(application).getContacts(includeUnstarred = false)

        assertEquals(listOf("Ada"), contacts.map { it.label })
        val query = provider.queries.single()
        assertEquals(
            listOf(
                ContactsContract.CommonDataKinds.Phone._ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                ContactsContract.CommonDataKinds.Phone.IS_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            query.projection,
        )
        assertEquals("${ContactsContract.CommonDataKinds.Phone.STARRED}='1' ", query.selection)
        assertEquals("true", query.uri.getQueryParameter(ContactsContract.Contacts.EXTRA_ADDRESS_BOOK_INDEX))
        assertEquals(
            "the row is identified by its call URI, not by a dialer package",
            contacts.single().intent?.data.toString(),
            contacts.single().packageName,
        )
    }

    @Test
    fun theWholeAddressBookIsReadWithNoSelectionWhenUnstarredContactsAreIncluded() {
        provider.rows = listOf(
            Row(id = 1, name = "Ada", number = "+39123"),
            Row(id = 2, name = "Bob", number = "+39456", starred = false),
        )

        val contacts = ContactsDAO(application).getContacts(includeUnstarred = true)

        assertEquals(listOf("Ada", "Bob"), contacts.map { it.label })
        assertNull(
            "an unstarred-inclusive read must not narrow the provider query to the starred rows",
            provider.queries.single().selection,
        )
    }

    @Test
    fun aContactRowIsTrackedUnderTheCallUriItsLaunchIsLoggedWith() {
        provider.rows = listOf(Row(id = 1, name = "Ada", number = "+39123"))

        val contact = ContactsDAO(application).getContacts(includeUnstarred = false).single()

        assertTrue("an intent-only row is trackable", contact.canTrack)
        assertNull(contact.appInfo)
        // The row identity is exactly the key launch() logs for it, so the launch-log ranking can
        // resolve the logged key back to this row.
        assertEquals(contact.intent?.data.toString(), contact.packageName)
    }

    @Test
    fun rowsWithoutADisplayNameOrANumberAreSkipped() {
        provider.rows = listOf(
            Row(id = 1, name = null, number = "+39111"),
            Row(id = 2, name = "   ", number = "+39112"),
            Row(id = 3, name = "NoNumber", number = null),
            Row(id = 4, name = "BlankNumber", number = "  "),
            Row(id = 5, name = "Ada", number = "+39123"),
        )

        val contacts = ContactsDAO(application).getContacts(includeUnstarred = true)

        assertEquals(listOf("Ada"), contacts.map { it.label })
    }

    @Test
    fun aRowWithoutANormalizedNumberIsCallableFromItsStoredNumber() {
        provider.rows = listOf(
            Row(id = 1, name = "Ada", number = null, rawNumber = "+39 123 456"),
        )

        val contact = ContactsDAO(application).getContacts(includeUnstarred = true).single()

        assertEquals(
            "a missing NORMALIZED_NUMBER must not drop a contact that has a number",
            "+39123456",
            contact.intent?.data?.schemeSpecificPart,
        )
        assertEquals(contact.intent?.data.toString(), contact.packageName)
    }

    @Test
    fun aRowWithNeitherANormalizedNorAStoredNumberIsSkipped() {
        provider.rows = listOf(
            Row(id = 1, name = "NoNumber", number = null, rawNumber = null),
        )

        assertEquals(
            emptyList<Any>(),
            ContactsDAO(application).getContacts(includeUnstarred = true),
        )
    }

    @Test
    fun eachRowDialsItsOwnNumberFromItsIntent() {
        provider.rows = listOf(Row(id = 1, name = "Ada", number = "+39123"))

        val contact = ContactsDAO(application).getContacts(includeUnstarred = false).single()

        assertEquals("tel", contact.intent?.data?.scheme)
        assertEquals("+39123", contact.intent?.data?.schemeSpecificPart)
    }

    @Test
    fun aThumbnailUriIsCarriedOnTheRowInsteadOfADecodedIcon() {
        provider.rows = listOf(Row(id = 1, name = "Ada", number = "+39123", photoUri = photoUri(1)))

        val contact = ContactsDAO(application).getContacts(includeUnstarred = false).single()

        assertEquals(photoUri(1), contact.photoUri)
        assertNull("the read must not decode thumbnails", contact.icon)
        assertTrue(
            "a list read must never touch the thumbnail provider",
            provider.thumbnailRequests.isEmpty(),
        )
    }

    @Test
    fun aContactWithoutAThumbnailCarriesNoPhotoUri() {
        provider.rows = listOf(
            Row(id = 1, name = "Ada", number = "+39123", photoUri = null),
            Row(id = 2, name = "Bob", number = "+39456", photoUri = "   "),
        )

        val contacts = ContactsDAO(application).getContacts(includeUnstarred = true)

        assertEquals(listOf(null, null), contacts.map { it.photoUri })
    }

    @Test
    fun everyNumberOfAContactStaysCallableInsteadOfOneRowPerLabel() {
        provider.rows = listOf(
            Row(id = 1, name = "Ada", number = "+39111"),
            Row(id = 2, name = "Ada", number = "+39222"),
            Row(id = 3, name = "Bob", number = "+39333"),
        )

        val contacts = ContactsDAO(application).getContacts(includeUnstarred = true)

        assertEquals(listOf("Ada", "Ada", "Bob"), contacts.map { it.label })
        assertEquals(
            listOf("+39111", "+39222", "+39333"),
            contacts.map { it.intent?.data?.schemeSpecificPart },
        )
    }

    @Test
    fun twoContactsSharingADisplayNameStayIndependentlyCallable() {
        provider.rows = listOf(
            Row(id = 1, name = "Dad", number = "+39111"),
            Row(id = 2, name = "Dad", number = "+39222"),
        )

        val contacts = ContactsDAO(application).getContacts(includeUnstarred = true)

        assertEquals(
            "a display name is not an identity, so it must not collapse two people",
            listOf("+39111", "+39222"),
            contacts.map { it.intent?.data?.schemeSpecificPart },
        )
    }

    @Test
    fun aNumberTheProviderReturnsTwiceIsOneRow() {
        provider.rows = listOf(
            Row(id = 1, name = "Ada", number = "+39111"),
            Row(id = 2, name = "Ada", number = "+39111"),
        )

        val contacts = ContactsDAO(application).getContacts(includeUnstarred = true)

        assertEquals(1, contacts.size)
    }

    private fun photoUri(index: Int): String = "content://${ContactsContract.AUTHORITY}/photo/$index"

    /**
     * A [NeurhomeApplication] with the framework base context attached but [Application.onCreate]
     * deliberately unrun: [ContactsDAO] only needs the application's `contentResolver`, and the real
     * `onCreate` opens the on-disk database and starts collectors that have nothing to do with the
     * contact list.
     */
    private fun unstartedApplication(): NeurhomeApplication {
        val app = NeurhomeApplication()
        val attachBaseContext =
            ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
        attachBaseContext.isAccessible = true
        attachBaseContext.invoke(app, RuntimeEnvironment.getApplication())
        return app
    }

    private data class Row(
        val id: Long,
        val name: String?,
        /** The normalized number column; null models a row whose provider never normalized it. */
        val number: String?,
        val photoUri: String? = null,
        val lookupKey: String = "lookup-$id",
        val isPrimary: String = "1",
        val starred: Boolean = true,
        /** The stored (possibly formatted) number column. */
        val rawNumber: String? = number,
    )

    private class FakeContactsProvider : ContentProvider() {
        data class Query(val uri: Uri, val projection: List<String>, val selection: String?)

        var rows: List<Row> = emptyList()
        val queries = mutableListOf<Query>()
        val thumbnailRequests = mutableListOf<String>()

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            queries += Query(uri, projection?.toList().orEmpty(), selection)
            val columns = projection ?: return MatrixCursor(emptyArray())
            // The provider applies the selection itself; the DAO's starred read must see only the
            // starred rows, exactly like the real provider.
            val selected = rows.filter { row ->
                selection == null || !selection.contains(STARRED) || row.starred
            }
            return MatrixCursor(columns).apply {
                selected.forEach { row ->
                    addRow(
                        arrayOf<Any?>(
                            row.id,
                            row.name,
                            row.lookupKey,
                            row.photoUri,
                            row.number,
                            row.isPrimary,
                            row.rawNumber,
                        )
                    )
                }
            }
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }

    private companion object {
        val STARRED = ContactsContract.CommonDataKinds.Phone.STARRED
    }
}
