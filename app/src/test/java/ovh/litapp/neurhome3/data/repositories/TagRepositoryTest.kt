package ovh.litapp.neurhome3.data.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.litapp.neurhome3.data.dao.TagDao
import ovh.litapp.neurhome3.data.dao.ApplicationTagDao
import ovh.litapp.neurhome3.data.models.ApplicationTag
import ovh.litapp.neurhome3.data.models.Tag

class TagRepositoryTest {
    private class FakeTagDao : TagDao {
        private val stored = MutableStateFlow<List<Tag>>(emptyList())

        override suspend fun insert(tag: Tag): Long {
            if (stored.value.any { it.name == tag.name }) return -1L
            stored.value = stored.value + tag
            return stored.value.lastIndex.toLong()
        }

        override fun list(): Flow<List<Tag>> = stored.map {
            it.sortedWith(
                compareBy<Tag> { tag -> tag.position }
                    .thenComparator { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name) }
            )
        }

        override suspend fun delete(name: String) {
            stored.value = stored.value.filterNot { it.name == name }
        }

        override suspend fun updatePosition(name: String, position: Int) {
            stored.value = stored.value.map { if (it.name == name) it.copy(position = position) else it }
        }
    }

    private class FakeApplicationTagDao : ApplicationTagDao {
        private val stored = MutableStateFlow<List<ApplicationTag>>(emptyList())

        override suspend fun insert(assignment: ApplicationTag): Long {
            if (assignment in stored.value) return -1L
            stored.value += assignment
            return 1L
        }

        override suspend fun delete(packageName: String, profile: Int, tagName: String) {
            stored.value = stored.value.filterNot {
                it.packageName == packageName && it.profile == profile && it.tagName == tagName
            }
        }

        override suspend fun deleteForApplication(packageName: String, profile: Int) {
            stored.value = stored.value.filterNot {
                it.packageName == packageName && it.profile == profile
            }
        }

        override suspend fun deleteForTag(tagName: String) {
            stored.value = stored.value.filterNot { it.tagName == tagName }
        }

        override fun list() = stored

        override fun listForApplication(packageName: String, profile: Int) =
            kotlinx.coroutines.flow.flow {
                emit(stored.first().filter {
                    it.packageName == packageName && it.profile == profile
                }.map { it.tagName })
            }
    }

    @Test
    fun blankAndDuplicateNamesAreRejected() = runBlocking {
        val repository = newRepository()

        assertFalse(repository.createTag("   "))
        assertTrue(repository.createTag(" Work "))
        assertFalse(repository.createTag("Work"))
    }

    @Test
    fun assignmentsCanBeAddedRemovedAndAreIsolated() = runBlocking {
        val repository = newRepository()

        repository.setTags("one", 10, setOf("Work", "Play"))
        repository.setTags("one", 10, setOf("Work"))
        repository.setTags("one", 11, setOf("Play"))

        assertEquals(listOf("Work"), repository.tagsForApplication("one", 10).first())
        assertEquals(listOf("Play"), repository.tagsForApplication("one", 11).first())
        assertEquals(2, repository.assignments.first().size)
    }

    @Test
    fun finalSelectionReplacesAllPreviousAssignments() = runBlocking {
        val repository = newRepository()
        repository.setTags("one", 10, setOf("Work", "Play"))

        repository.setTags("one", 10, setOf("Read"))

        assertEquals(listOf("Read"), repository.tagsForApplication("one", 10).first())
    }

    @Test
    fun deletingTagDeletesItsAssignmentsAndLeavesOtherTagsUnchanged() = runBlocking {
        val repository = newRepository()
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
        val repository = newRepository()
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
        val repository = newRepository()
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
    fun zeroAndUnknownMoveRequestsLeavePositionsUnchanged() = runBlocking {
        val repository = newRepository()
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
        val repository = newRepository()
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
        val repository = newRepository()
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

    private fun newRepository(
        tagDao: FakeTagDao = FakeTagDao(),
        applicationTagDao: FakeApplicationTagDao = FakeApplicationTagDao()
    ) = TagRepository(tagDao, applicationTagDao) { block -> block() }
}
