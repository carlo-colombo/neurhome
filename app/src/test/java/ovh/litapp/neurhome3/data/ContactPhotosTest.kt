package ovh.litapp.neurhome3.data

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.ContactsContract
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileNotFoundException
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

/**
 * Covers the lazy half of the contact-photo path: a row carries only its thumbnail URI, and this
 * cache decodes it exactly once per URI, never on a plain list read.
 *
 * [GraphicsMode.Mode.NATIVE] is required here: `ContentResolver.loadThumbnail` goes through
 * `ImageDecoder`, and the default legacy graphics mode returns no decoder at all, so no photo could
 * ever be decoded and the cache assertions would prove nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContactPhotosTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var application: Application
    private lateinit var provider: FakeContactsProvider
    private lateinit var photos: ContactPhotos

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        provider = ContentProviderController
            .of(FakeContactsProvider(photoFile()))
            .create(ContactsContract.AUTHORITY)
            .get()
        photos = ContactPhotos(application.contentResolver, application.resources)
    }

    @Test
    fun aThumbnailUriIsDecodedOnTheFirstRequest() = runBlocking {
        val photo = photos.load(photoUri(1))

        assertNotNull(photo)
        assertEquals(listOf(photoUri(1)), provider.thumbnailRequests)
    }

    @Test
    fun aDecodedPhotoIsReusedForEveryLaterRequestForTheSameUri() = runBlocking {
        val first = photos.load(photoUri(1))

        val second = photos.load(photoUri(1))

        assertSame("a decoded photo must not be read or decoded again", first, second)
        assertEquals(1, provider.thumbnailRequests.size)
        assertSame(first, photos.cached(photoUri(1)))
    }

    @Test
    fun anUnknownUriHasNothingCachedAndTouchesNoProvider() {
        assertNull(photos.cached(photoUri(1)))

        assertTrue(provider.thumbnailRequests.isEmpty())
    }

    @Test
    fun anUnreadableThumbnailResolvesToNoPhoto() = runBlocking {
        assertNull(photos.load(UNREADABLE_PHOTO_URI))
        assertNull(photos.cached(UNREADABLE_PHOTO_URI))
    }

    @Test
    fun aPhotoThatFailsOnceIsNotFalselyReportedAsCached() = runBlocking {
        assertNull(photos.load(UNREADABLE_PHOTO_URI))

        assertNull(
            "a failed decode must not be memoised as a photo",
            photos.load(UNREADABLE_PHOTO_URI),
        )
    }

    @Test
    fun theCacheHoldsNoMoreThanTheBound() = runBlocking {
        repeat(CACHE_SIZE) { index -> photos.load(photoUri(index)) }
        assertEquals(CACHE_SIZE, provider.thumbnailRequests.size)

        // One more than the bound cannot be retained: inserting it drops the eldest entry.
        photos.load(photoUri(CACHE_SIZE))

        assertEquals(CACHE_SIZE + 1, provider.thumbnailRequests.size)
        assertNull("the evicted photo is gone from the cache", photos.cached(photoUri(0)))
        assertNotNull("the newest photo stays cached", photos.cached(photoUri(CACHE_SIZE)))
    }

    private fun photoUri(index: Int): String = "content://${ContactsContract.AUTHORITY}/photo/$index"

    private fun photoFile(): File {
        val file = temporaryFolder.newFile("thumb.png")
        ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", file)
        return file
    }

    private class FakeContactsProvider(private val photo: File) : ContentProvider() {
        val thumbnailRequests = mutableListOf<String>()

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

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
        /** Mirrors `PHOTO_CACHE_SIZE`; the bound is the behaviour under test. */
        const val CACHE_SIZE = 64

        const val UNREADABLE_PHOTO_URI = "content://${ContactsContract.AUTHORITY}/photo/missing"
    }
}
