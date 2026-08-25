package ovh.litapp.neurhome3.data.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.litapp.neurhome3.data.dao.TagDao
import ovh.litapp.neurhome3.data.models.Tag

class TagRepositoryTest {
    private class FakeTagDao : TagDao {
        private val stored = MutableStateFlow<List<Tag>>(emptyList())

        override suspend fun insert(tag: Tag): Long {
            if (stored.value.any { it.name == tag.name }) return -1L
            stored.value = stored.value + tag
            return stored.value.lastIndex.toLong()
        }

        override fun list(): Flow<List<Tag>> = stored
    }

    @Test
    fun blankAndDuplicateNamesAreRejected() = runBlocking {
        val repository = TagRepository(FakeTagDao())

        assertFalse(repository.createTag("   "))
        assertTrue(repository.createTag(" Work "))
        assertFalse(repository.createTag("Work"))
    }
}
