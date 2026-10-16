package ovh.litapp.neurhome3.data.dao

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.models.ApplicationTag

/**
 * Executes the production `ORDER BY tagName COLLATE NOCASE` of [ApplicationTagDao.listForApplication]
 * against a real in-memory Room database. The application detail row renders this list as the tag
 * chips for one package/profile, so both the ordering and the package/profile scoping are asserted
 * here; `TagRepositoryTest`'s fake DAO re-implements the same ordering in Kotlin.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ApplicationTagDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ApplicationTagDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.applicationTagDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun tagsOfOneApplicationAreOrderedCaseInsensitivelyByName() = runTest {
        // "apple" and "Banana": `COLLATE NOCASE` puts apple first, the default BINARY collation
        // would put "Banana" (0x42) first.
        dao.insert(ApplicationTag("com.example.app", profile = 0, tagName = "weekend"))
        dao.insert(ApplicationTag("com.example.app", profile = 0, tagName = "Banana"))
        dao.insert(ApplicationTag("com.example.app", profile = 0, tagName = "apple"))

        assertEquals(
            listOf("apple", "Banana", "weekend"),
            dao.listForApplication("com.example.app", profile = 0).first(),
        )
    }

    @Test
    fun tagsAreScopedToThePackageAndProfileThatWereAssigned() = runTest {
        dao.insert(ApplicationTag("com.example.app", profile = 0, tagName = "work"))
        dao.insert(ApplicationTag("com.example.app", profile = 10, tagName = "private"))
        dao.insert(ApplicationTag("com.example.other", profile = 0, tagName = "other"))

        assertEquals(
            listOf("work"),
            dao.listForApplication("com.example.app", profile = 0).first(),
        )
        assertEquals(
            listOf("private"),
            dao.listForApplication("com.example.app", profile = 10).first(),
        )
        assertEquals(listOf("other"), dao.listForApplication("com.example.other", 0).first())
        assertEquals(emptyList<String>(), dao.listForApplication("com.example.missing", 0).first())
        assertEquals(3, dao.list().first().size)
    }
}
