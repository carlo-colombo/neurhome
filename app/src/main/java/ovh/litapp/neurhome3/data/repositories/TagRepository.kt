package ovh.litapp.neurhome3.data.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import ovh.litapp.neurhome3.data.dao.TagDao
import ovh.litapp.neurhome3.data.dao.ApplicationTagDao
import ovh.litapp.neurhome3.data.models.ApplicationTag
import ovh.litapp.neurhome3.data.models.Tag

class TagRepository(
    private val tagDao: TagDao,
    private val applicationTagDao: ApplicationTagDao? = null,
    private val database: RoomDatabase? = null,
) {
    val tags: Flow<List<Tag>> = tagDao.list()
    val assignments: Flow<List<ApplicationTag>> = applicationTagDao?.list() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    fun tagsForApplication(packageName: String, profile: Int): Flow<List<String>> =
        requireNotNull(applicationTagDao).listForApplication(packageName, profile)

    suspend fun setTags(packageName: String, profile: Int, tagNames: Set<String>) {
        val dao = requireNotNull(applicationTagDao)
        suspend fun replace() {
            dao.deleteForApplication(packageName, profile)
            tagNames.forEach { tagName ->
                dao.insert(ApplicationTag(packageName, profile, tagName))
            }
        }
        if (database == null) replace() else database.withTransaction { replace() }
    }

    suspend fun createTag(name: String): Boolean {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return false
        val position = tags.first().maxOfOrNull { it.position }?.plus(1) ?: 0
        return tagDao.insert(Tag(normalizedName, position)) != -1L
    }

    suspend fun deleteTag(name: String) {
        val assignments = requireNotNull(applicationTagDao)
        suspend fun delete() {
            assignments.deleteForTag(name)
            tagDao.delete(name)
            normalizePositions()
        }
        if (database == null) delete() else database.withTransaction { delete() }
    }

    suspend fun moveTag(name: String, direction: Int): Boolean {
        if (direction == 0) return false
        val ordered = tags.first()
        val currentIndex = ordered.indexOfFirst { it.name == name }
        val targetIndex = currentIndex + direction.coerceIn(-1, 1)
        if (currentIndex < 0 || targetIndex !in ordered.indices) return false

        suspend fun move() {
            val reordered = ordered.toMutableList().apply {
                add(targetIndex, removeAt(currentIndex))
            }
            reordered.forEachIndexed { index, tag -> tagDao.updatePosition(tag.name, index) }
        }
        if (database == null) move() else database.withTransaction { move() }
        return true
    }

    private suspend fun normalizePositions() {
        tags.first().forEachIndexed { index, tag -> tagDao.updatePosition(tag.name, index) }
    }
}
