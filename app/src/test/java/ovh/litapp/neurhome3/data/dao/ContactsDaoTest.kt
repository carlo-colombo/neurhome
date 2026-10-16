package ovh.litapp.neurhome3.data.dao

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.ContactsContract
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileNotFoundException
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ContentProviderController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ovh.litapp.neurhome3.application.NeurhomeApplication

/**
 * Drives [ContactsDAO.getStarredContacts] against a seeded [MatrixCursor] served by a fake
 * `com.android.contacts` provider, so the read-by-index projection, the null/blank name-or-number
 * row guard (regression lock for BUG-03), the label de-duplication and the bounded thumbnail cache
 * run as production code without a device contact list.
 *
 * [GraphicsMode.Mode.NATIVE] is required for the photo path: `ContentResolver.loadThumbnail` goes
 * through `ImageDecoder`, and the default legacy graphics mode returns no decoder at all, so no
 * photo could ever be cached and the cache bound would be untestable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContactsDaoTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var application: NeurhomeApplication
    private lateinit var provider: FakeContactsProvider

    @Before
    fun setUp() {
        application = unstartedApplication()
        provider = ContentProviderController
            .of(FakeContactsProvider(photoFile()))
            .create(ContactsContract.AUTHORITY)
            .get()
    }

    @Test
    fun theQueryReadsTheStarredPhoneRowsWithTheProjectionTheParserReadsByIndex() {
        provider.rows = listOf(Row(id = 1, name = "Ada", number = "+39123"))

        val contacts = ContactsDAO(application).getStarredContacts()

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
            ),
            query.projection,
        )
        assertEquals("${ContactsContract.CommonDataKinds.Phone.STARRED}='1' ", query.selection)
        assertEquals("true", query.uri.getQueryParameter(ContactsContract.Contacts.EXTRA_ADDRESS_BOOK_INDEX))
        assertEquals("com.google.android.dialer", contacts.single().packageName)
    }

    @Test
    fun rowsWithoutADisplayNameOrANumberAreSkippedInsteadOfCrashing() {
        provider.rows = listOf(
            Row(id = 1, name = null, number = "+39111"),
            Row(id = 2, name = "   ", number = "+39112"),
            Row(id = 3, name = "NoNumber", number = null),
            Row(id = 4, name = "BlankNumber", number = "  "),
            Row(id = 5, name = "Ada", number = "+39123"),
        )

        val contacts = ContactsDAO(application).getStarredContacts()

        assertEquals(listOf("Ada"), contacts.map { it.label })
    }

    @Test
    fun aMissingPhoneNumberIsStillDialableFromTheIntentOfTheKeptRow() {
        provider.rows = listOf(Row(id = 1, name = "Ada", number = "+39123"))

        val contact = ContactsDAO(application).getStarredContacts().single()

        assertEquals("tel", contact.intent?.data?.scheme)
        assertEquals("+39123", contact.intent?.data?.schemeSpecificPart)
    }

    @Test
    fun aContactWithoutAThumbnailHasNoIcon() {
        provider.rows = listOf(Row(id = 1, name = "Ada", number = "+39123", photoUri = null))

        val contact = ContactsDAO(application).getStarredContacts().single()

        assertNull(contact.icon)
        assertTrue(provider.thumbnailRequests.isEmpty())
    }

    @Test
    fun anUnreadableThumbnailHasNoIconEither() {
        provider.rows = listOf(
            Row(id = 1, name = "Ada", number = "+39123", photoUri = UNREADABLE_PHOTO_URI)
        )

        val contact = ContactsDAO(application).getStarredContacts().single()

        assertNull(contact.icon)
    }

    @Test
    fun theFirstRowOfADuplicateLabelWins() {
        provider.rows = listOf(
            Row(id = 1, name = "Ada", number = "+39111"),
            Row(id = 2, name = "Ada", number = "+39222"),
            Row(id = 3, name = "Bob", number = "+39333"),
        )

        val contacts = ContactsDAO(application).getStarredContacts()

        assertEquals(listOf("Ada", "Bob"), contacts.map { it.label })
        assertEquals("+39111", contacts.first().intent?.data?.schemeSpecificPart)
    }

    @Test
    fun aContactListThatFitsInTheCacheReusesEveryThumbnail() {
        provider.rows = (1..CACHE_SIZE).map { index -> rowWithThumbnail(index) }
        val dao = ContactsDAO(application)

        val firstRead = dao.getStarredContacts()
        assertEquals(CACHE_SIZE, firstRead.count { it.icon != null })
        assertEquals(CACHE_SIZE, provider.thumbnailRequests.size)

        dao.getStarredContacts()

        assertEquals(
            "photos cached by the previous read must not be decoded again",
            CACHE_SIZE,
            provider.thumbnailRequests.size,
        )
    }

    @Test
    fun theThumbnailCacheHoldsNoMoreThanTheBound() {
        provider.rows = (1..CACHE_SIZE + 1).map { index -> rowWithThumbnail(index) }
        val dao = ContactsDAO(application)

        val firstRead = dao.getStarredContacts()
        assertEquals(CACHE_SIZE + 1, firstRead.count { it.icon != null })
        assertEquals(CACHE_SIZE + 1, provider.thumbnailRequests.size)

        dao.getStarredContacts()

        // One photo more than the bound cannot be retained: the eviction on each insert drops the
        // eldest entry, which for a freshly loaded list is the next photo requested, so the second
        // read re-decodes all of them. An unbounded cache would re-decode none (the assertion
        // below is what pins the bound); the reuse itself is pinned above.
        assertEquals(CACHE_SIZE + 1, provider.thumbnailRequests.size - (CACHE_SIZE + 1))
    }

    @Test
    fun aSeededThumbnailIsResolvedIntoTheRowIcon() {
        provider.rows = listOf(Row(id = 1, name = "Ada", number = "+39123", photoUri = photoUri(1)))

        val contact = ContactsDAO(application).getStarredContacts().single()

        assertNotNull(contact.icon)
        assertEquals(listOf(photoUri(1)), provider.thumbnailRequests)
    }

    private fun rowWithThumbnail(index: Int) = Row(
        id = index.toLong(),
        name = "Contact $index",
        number = "+39123$index",
        photoUri = photoUri(index),
    )

    private fun photoUri(index: Int): String = "content://${ContactsContract.AUTHORITY}/photo/$index"

    private fun photoFile(): File {
        val file = temporaryFolder.newFile("thumb.png")
        ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", file)
        return file
    }

    /**
     * A [NeurhomeApplication] with the framework base context attached but [Application.onCreate]
     * deliberately unrun: [ContactsDAO] only needs the application's `contentResolver` and
     * `resources`, and the real `onCreate` opens the on-disk database and starts collectors that
     * have nothing to do with the starred-contact list.
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
        val number: String?,
        val photoUri: String? = null,
        val lookupKey: String = "lookup-$id",
        val isPrimary: String = "1",
    )

    private class FakeContactsProvider(private val photo: File) : ContentProvider() {
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
            return MatrixCursor(columns).apply {
                rows.forEach { row ->
                    addRow(
                        arrayOf<Any?>(
                            row.id,
                            row.name,
                            row.lookupKey,
                            row.photoUri,
                            row.number,
                            row.isPrimary,
                        )
                    )
                }
            }
        }

        override fun openTypedAssetFile(
            uri: Uri,
            mimeTypeFilter: String,
            opts: Bundle?,
        ): AssetFileDescriptor {
            thumbnailRequests += uri.toString()
            if (uri.toString() == UNREADABLE_PHOTO_URI) {
                throw FileNotFoundException(uri.toString())
            }
            return AssetFileDescriptor(
                ParcelFileDescriptor.open(photo, ParcelFileDescriptor.MODE_READ_ONLY),
                0L,
                photo.length(),
            )
        }

        override fun getType(uri: Uri): String? = "image/png"

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
        /** Mirrors `CONTACT_PHOTO_CACHE_SIZE`; the bound is the behaviour under test. */
        const val CACHE_SIZE = 64

        const val UNREADABLE_PHOTO_URI = "content://${ContactsContract.AUTHORITY}/photo/missing"
    }
}
