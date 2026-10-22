package ovh.litapp.neurhome3.data.repositories

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.os.UserManager
import android.provider.ContactsContract
import androidx.room.Room
import androidx.room.withTransaction
import java.util.Collections
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ContentProviderController
import org.robolectric.annotation.Config
import ovh.litapp.neurhome3.ApplicationService
import ovh.litapp.neurhome3.application.NeurhomeApplication
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.Application as HomeApplication
import ovh.litapp.neurhome3.data.dao.ContactsDAO
import ovh.litapp.neurhome3.data.models.Setting

/**
 * Pins the repository half of the contact scope: which rows [NeurhomeRepository.contacts] reads for
 * each persisted [ContactCandidates] value, and with which provider selection. The DAO's parsing is
 * covered by `ContactsDaoTest`; this is the wiring that decides whether an unstarred contact is
 * ever offered at all.
 *
 * The shared flow loads on the repository's Main scope, so the collector runs on IO and the main
 * looper is drained until the read lands, exactly as `ContactRowRankingTest` does for the
 * statistics simulation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = NeurhomeApplication::class)
class ContactScopeTest {
    private lateinit var application: NeurhomeApplication
    private lateinit var database: AppDatabase
    private lateinit var provider: FakeContactsProvider

    @Before
    fun setUp() {
        application = RuntimeEnvironment.getApplication() as NeurhomeApplication
        shadowOf(application).grantPermissions(Manifest.permission.READ_CONTACTS)
        // The repository's call logging uses the owner profile, which a device always has.
        val userManager = application.getSystemService(Context.USER_SERVICE) as UserManager
        shadowOf(userManager).addProfile(0, 0, "User", 0)

        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        provider = ContentProviderController
            .of(FakeContactsProvider())
            .create(ContactsContract.AUTHORITY)
            .get()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun theAllScopeReadsTheUnstarredNumberToo() {
        provider.rows = listOf(
            Row(name = "Ada", number = "+39111", starred = true),
            Row(name = "Bob", number = "+39222", starred = false),
        )
        storeScope(ContactCandidates.ALL)

        val contacts = awaitContacts { it.isNotEmpty() }

        assertEquals(listOf("Ada", "Bob"), contacts.map { it.label })
        assertNull(
            "the whole-address-book read must not narrow the provider query to starred rows",
            provider.queries.firstOrNull()?.selection,
        )
    }

    @Test
    fun theStarredScopeKeepsTheStarredSelection() {
        provider.rows = listOf(Row(name = "Ada", number = "+39111", starred = true))
        storeScope(ContactCandidates.STARRED)

        awaitContacts { it.isNotEmpty() }

        assertEquals(
            "${ContactsContract.CommonDataKinds.Phone.STARRED}='1' ",
            provider.queries.firstOrNull()?.selection,
        )
    }

    private fun storeScope(candidates: ContactCandidates) = runBlocking(Dispatchers.IO) {
        // Written through the DAO, because the setter writes on its own IO scope and the
        // application's database refuses main-thread queries.
        application.settingsRepository.settingDao.upsert(
            Setting(CONTACT_CANDIDATES_SETTING, candidates.name)
        )
    }

    private fun awaitContacts(until: (List<HomeApplication>) -> Boolean): List<HomeApplication> {
        val repository = repository()
        val result = CompletableFuture<List<HomeApplication>>()
        val collection = CoroutineScope(Dispatchers.IO).launch {
            result.complete(repository.contacts.first(until))
        }
        val deadline = System.nanoTime() + AWAIT_TIMEOUT_NANOS
        while (!result.isDone) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.nanoTime() < deadline) { "the contact rows never loaded for the chosen scope" }
            Thread.sleep(AWAIT_POLL_MILLIS)
        }
        collection.cancel()
        repository.close()
        return result.getNow(emptyList())
    }

    private fun repository() = NeurhomeRepository(
        applicationLogEntryDao = database.applicationLogEntryDao(),
        additionalPackageMetadataDao = database.hiddenPackageDao(),
        applicationService = ApplicationService(
            packageManager = application.packageManager,
            launcherApps = application.launcherApps,
            userManager = application.getSystemService(Context.USER_SERVICE) as UserManager,
        ),
        contactsDAO = ContactsDAO(application),
        application = application,
        database = database,
        launcherApps = application.launcherApps,
        homeAppRankingSource = RoomHomeAppRankingSource(
            applicationLogEntryDao = database.applicationLogEntryDao(),
            quietModes = { emptyMap() },
        ),
        tagRepository = TagRepository(database.tagDao(), database.applicationTagDao()) { block ->
            database.withTransaction { block() }
        },
        databaseUriProvider = { Uri.EMPTY },
    )

    private data class Row(
        val name: String,
        val number: String,
        val starred: Boolean,
    )

    /**
     * Serves the production projection by index and applies the starred selection the way the real
     * provider does, so a read that forgets to widen the selection cannot silently pass.
     */
    private class FakeContactsProvider : ContentProvider() {
        data class Query(val selection: String?)

        var rows: List<Row> = emptyList()
        val queries: MutableList<Query> = Collections.synchronizedList(mutableListOf())

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            queries += Query(selection)
            val columns = projection ?: return MatrixCursor(emptyArray())
            val starredOnly = selection?.contains(ContactsContract.CommonDataKinds.Phone.STARRED) == true
            return MatrixCursor(columns).apply {
                rows.filter { !starredOnly || it.starred }.forEachIndexed { index, row ->
                    addRow(
                        arrayOf<Any?>(
                            index.toLong(),
                            row.name,
                            "lookup-$index",
                            null,
                            row.number,
                            "1",
                            row.number,
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
        const val AWAIT_TIMEOUT_NANOS = 5_000_000_000L
        const val AWAIT_POLL_MILLIS = 10L
    }
}
