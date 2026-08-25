package ovh.litapp.neurhome3.data.repositories

import kotlinx.coroutines.flow.Flow
import ovh.litapp.neurhome3.data.dao.TagDao
import ovh.litapp.neurhome3.data.models.Tag

class TagRepository(private val tagDao: TagDao) {
    val tags: Flow<List<Tag>> = tagDao.list()

    suspend fun createTag(name: String): Boolean {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return false
        return tagDao.insert(Tag(normalizedName)) != -1L
    }
}
