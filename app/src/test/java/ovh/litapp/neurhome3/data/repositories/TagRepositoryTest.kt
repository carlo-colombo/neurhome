package ovh.litapp.neurhome3.data.repositories

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.models.ApplicationTag
import ovh.litapp.neurhome3.data.models.Tag

/**
 * Runs [TagRepository] over a real in-memory Room database and the production transaction runner.
 *
 * The ordering is the load-bearing part: [TagRepository.moveTag] derives the current index from the
 * order `TagDao.list` returns and writes the new positions back, so it depends on the DAO's
 * `ORDER BY position, name COLLATE NOCASE` while renumbering the slots. A fake DAO that re-implements
 * that order in Kotlin cannot catch the two from drifting apart - `movingATagFollowsTheDaosOrder`
 * fails if the production ordering is dropped - and only real SQL executes the inserts, deletes,
 * renumbering and per-application tag lookup this repository composes them from.
 *
 * `TagDaoTest` pins the query itself; this class pins the repository on top of it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TagRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: TagRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = TagRepository(
            tagDao = database.tagDao(),
            applicationTagDao = database.applicationTagDao(),
        ) { block -> database.withTransaction { block() } }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun blankAndDuplicateNamesAreRejected() = runBlocking {
        assertFalse(repository.createTag("   "))
        assertTrue(repository.createTag(" Work "))
        assertFalse(repository.createTag("Work"))
    }

    @Test
    fun assignmentsCanBeAddedRemovedAndAreIsolated() = runBlocking {
        repository.setTags("one", 10, setOf("Work", "Play"))
        repository.setTags("one", 10, setOf("Work"))
        repository.setTags("one", 11, setOf("Play"))

        assertEquals(listOf("Work"), repository.tagsForApplication("one", 10).first())
        assertEquals(listOf("Play"), repository.tagsForApplication("one", 11).first())
        assertEquals(2, repository.assignments.first().size)
    }

    @Test
    fun finalSelectionReplacesAllPreviousAssignments() = runBlocking {
        repository.setTags("one", 10, setOf("Work", "Play"))

        repository.setTags("one", 10, setOf("Read"))

        assertEquals(listOf("Read"), repository.tagsForApplication("one", 10).first())
    }

    @Test
    fun tagNamesForAnApplicationComeBackInCaseInsensitiveOrder() = runBlocking {
        // `ORDER BY tagName COLLATE NOCASE` keeps "apple" before "Banana"; the default BINARY
        // collation would put "Banana" (0x42) first.
        repository.setTags("one", 10, setOf("Banana", "apple"))

        assertEquals(listOf("apple", "Banana"), repository.tagsForApplication("one", 10).first())
    }

    @Test
    fun deletingTagDeletesItsAssignmentsAndLeavesOtherTagsUnchanged() = runBlocking {
        repository.createTag("Work")
        repository.createTag("Play")
        repository.setTags("one", 10, setOf("Work", "Play"))
        repository.setTags("two", 10, setOf("Play"))

        repository.deleteTag("Work")

        assertEquals(
            listOf("Play" to 0),
            repository.tags.first().map { it.name to it.position }
        )
        assertEquals(
            listOf(ApplicationTag("one", 10, "Play"), ApplicationTag("two", 10, "Play")),
            repository.assignments.first()
        )
    }

    @Test
    fun deletingAMiddleTagKeepsTheRemainingPositionsContiguous() = runBlocking {
        repository.createTag("Work")
        repository.createTag("Play")
        repository.createTag("Read")

        repository.deleteTag("Play")

        assertEquals(
            listOf("Work" to 0, "Read" to 1),
            repository.tags.first().map { it.name to it.position }
        )
    }

    @Test
    fun movingTagsPersistsOrderAndRespectsBoundaries() = runBlocking {
        repository.createTag("Work")
        repository.createTag("Play")
        repository.createTag("Read")

        assertFalse(repository.moveTag("Work", -1))
        assertTrue(repository.moveTag("Work", 1))
        assertEquals(
            listOf("Play" to 0, "Work" to 1, "Read" to 2),
            repository.tags.first().map { it.name to it.position }
        )
        assertTrue(repository.moveTag("Read", -1))
        assertEquals(
            listOf("Play" to 0, "Read" to 1, "Work" to 2),
            repository.tags.first().map { it.name to it.position }
        )
        assertFalse(repository.moveTag("Work", 1))
    }

    @Test
    fun movingATagFollowsTheDaosOrderRatherThanTheInsertionOrder() = runBlocking {
        // Stored in the order they were written, but their positions order them Play, Read, Work.
        // A lookup that returned rows in insertion order would place "Read" last, push its target
        // index past the end of the list, and refuse the move.
        database.tagDao().insert(Tag(name = "Work", position = 2))
        database.tagDao().insert(Tag(name = "Play", position = 0))
        database.tagDao().insert(Tag(name = "Read", position = 1))

        assertTrue(repository.moveTag("Read", TagMoveDirection.NEXT))

        assertEquals(
            listOf("Play" to 0, "Work" to 1, "Read" to 2),
            repository.tags.first().map { it.name to it.position }
        )
    }

    @Test
    fun zeroAndUnknownMoveRequestsLeavePositionsUnchanged() = runBlocking {
        repository.createTag("Work")
        repository.createTag("Play")
        repository.createTag("Read")

        assertFalse(repository.moveTag("Work", 0))
        assertFalse(repository.moveTag("Missing", 1))

        assertEquals(
            listOf("Work" to 0, "Play" to 1, "Read" to 2),
            repository.tags.first().map { it.name to it.position }
        )
    }

    @Test
    fun interleavedCreateMoveAndDeleteKeepPositionsContiguous() = runBlocking {
        repository.createTag("Work")
        repository.createTag("Play")
        repository.createTag("Read")

        repository.moveTag("Read", -1)
        repository.deleteTag("Work")
        repository.createTag("Extra")
        repository.moveTag("Extra", -1)
        repository.moveTag("Read", 1)

        val tags = repository.tags.first()
        assertEquals(
            listOf("Extra" to 0, "Read" to 1, "Play" to 2),
            tags.map { it.name to it.position }
        )
        assertEquals(tags.indices.toList(), tags.map { it.position })
    }

    @Test
    fun creatingAfterADeleteAppendsAfterTheLastSurvivingPosition() = runBlocking {
        repository.createTag("Work")
        repository.createTag("Play")
        repository.createTag("Read")
        repository.deleteTag("Play")

        assertTrue(repository.createTag("Extra"))

        assertEquals(
            listOf("Work" to 0, "Read" to 1, "Extra" to 2),
            repository.tags.first().map { it.name to it.position }
        )
    }
}
