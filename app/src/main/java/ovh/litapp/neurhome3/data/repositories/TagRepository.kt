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
        val current = dao.listForApplication(packageName, profile)
        // This method is called from the ViewModel with a snapshot; the DAO remains the source of truth.
        val currentNames = current.first().toSet()
        tagNames.minus(currentNames).forEach {
            dao.insert(ApplicationTag(packageName, profile, it))
        }
        currentNames.minus(tagNames).forEach {
            dao.delete(packageName, profile, it)
        }
    }

    suspend fun createTag(name: String): Boolean {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return false
        return tagDao.insert(Tag(normalizedName)) != -1L
    }

    suspend fun deleteTag(name: String) {
        val assignments = requireNotNull(applicationTagDao)
        suspend fun delete() {
            assignments.deleteForTag(name)
            tagDao.delete(name)
        }
        if (database == null) delete() else database.withTransaction { delete() }
    }
}
