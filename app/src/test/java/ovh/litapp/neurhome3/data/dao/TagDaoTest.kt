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
import ovh.litapp.neurhome3.data.models.Tag

/**
 * Runs the real `Tag` SQL against a fresh in-memory Room database. The ordering is the load-bearing
 * part: `Tag.position` is a contiguous slot index and `TagRepository` re-derives it from the order
 * [TagDao.list] returns, so a query that ordered by name alone (or by insertion rowid) would both
 * reorder the category pages and renumber every slot on the next move.
 *
 * [ovh.litapp.neurhome3.data.repositories.TagRepositoryTest] covers the slot arithmetic on a fake
 * DAO that re-implements this ordering in Kotlin; only this test executes the production
 * `ORDER BY position, name COLLATE NOCASE`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TagDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: TagDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.tagDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun tagsAreOrderedByPositionThenCaseInsensitivelyByName() = runTest {
        // Inserted out of order, and "apple"/"Banana" share position 0: `COLLATE NOCASE` puts
        // apple first, while the default BINARY collation would put "Banana" (0x42) first.
        dao.insert(Tag(name = "Zebra", position = 2))
        dao.insert(Tag(name = "cherry", position = 1))
        dao.insert(Tag(name = "Banana", position = 0))
        dao.insert(Tag(name = "apple", position = 0))

        assertEquals(
            listOf("apple", "Banana", "cherry", "Zebra"),
            dao.list().first().map { it.name },
        )
        assertEquals(
            listOf(0, 0, 1, 2),
            dao.list().first().map { it.position },
        )
    }

    @Test
    fun aDuplicateNameIsIgnoredAndKeepsItsStoredPosition() = runTest {
        dao.insert(Tag(name = "apple", position = 0))
        dao.insert(Tag(name = "Banana", position = 1))

        val duplicateInsert = dao.insert(Tag(name = "apple", position = 7))

        assertEquals(-1L, duplicateInsert)
        assertEquals(
            listOf("apple" to 0, "Banana" to 1),
            dao.list().first().map { it.name to it.position },
        )
    }

    @Test
    fun updatePositionIsVisibleThroughTheListedTags() = runTest {
        dao.insert(Tag(name = "apple", position = 0))
        dao.insert(Tag(name = "Banana", position = 1))

        dao.updatePosition(name = "Banana", position = 0)
        dao.updatePosition(name = "apple", position = 1)

        assertEquals(
            listOf("Banana" to 0, "apple" to 1),
            dao.list().first().map { it.name to it.position },
        )
    }
}
