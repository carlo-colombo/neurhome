package ovh.litapp.neurhome3.data.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
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
            it.sortedWith(compareBy<Tag> { tag -> tag.position }.thenBy { tag -> tag.name.lowercase() })
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
        val repository = TagRepository(FakeTagDao())

        assertFalse(repository.createTag("   "))
        assertTrue(repository.createTag(" Work "))
        assertFalse(repository.createTag("Work"))
    }

    @Test
    fun assignmentsCanBeAddedRemovedAndAreIsolated() = runBlocking {
        val repository = TagRepository(FakeTagDao(), FakeApplicationTagDao())

        repository.setTags("one", 10, setOf("Work", "Play"))
        repository.setTags("one", 10, setOf("Work"))
        repository.setTags("one", 11, setOf("Play"))

        assertTrue(repository.tagsForApplication("one", 10).first() == listOf("Work"))
        assertTrue(repository.tagsForApplication("one", 11).first() == listOf("Play"))
        assertTrue(repository.assignments.first().size == 2)
    }

    @Test
    fun deletingTagDeletesItsAssignmentsAndLeavesOtherTagsUnchanged() = runBlocking {
        val tagDao = FakeTagDao()
        val assignmentDao = FakeApplicationTagDao()
        val repository = TagRepository(tagDao, assignmentDao)
        repository.createTag("Work")
        repository.createTag("Play")
        repository.setTags("one", 10, setOf("Work", "Play"))
        repository.setTags("two", 10, setOf("Play"))

        repository.deleteTag("Work")

        assertTrue(repository.tags.first().map { it.name } == listOf("Play"))
        assertTrue(repository.assignments.first() == listOf(ApplicationTag("one", 10, "Play"), ApplicationTag("two", 10, "Play")))
    }

    @Test
    fun movingTagsPersistsOrderAndRespectsBoundaries() = runBlocking {
        val dao = FakeTagDao()
        val repository = TagRepository(dao)
        repository.createTag("Work")
        repository.createTag("Play")
        repository.createTag("Read")

        assertFalse(repository.moveTag("Work", -1))
        assertTrue(repository.moveTag("Work", 1))
        assertTrue(repository.tags.first().map { it.name } == listOf("Play", "Work", "Read"))
        assertTrue(repository.moveTag("Read", -1))
        assertTrue(repository.tags.first().map { it.name } == listOf("Play", "Read", "Work"))
        assertFalse(repository.moveTag("Work", 1))
    }
}
