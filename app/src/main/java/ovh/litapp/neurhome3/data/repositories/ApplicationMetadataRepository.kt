package ovh.litapp.neurhome3.data.repositories

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.ApplicationVisibility
import ovh.litapp.neurhome3.data.dao.AdditionalPackageMetadataDao
import ovh.litapp.neurhome3.data.dao.UpdateAlias
import ovh.litapp.neurhome3.data.dao.UpdateVisibility
import ovh.litapp.neurhome3.data.models.HiddenPackageType

/**
 * Writes to the per-application metadata behind the launcher rows: visibility, alias, and tag
 * assignments. The reads stay where they belong (the launcher-apps source merges alias and
 * visibility into every row, [TagRepository] serves the tag assignments), so this type is the one
 * place a metadata write happens and the one place the ranking's visibility version is bumped.
 */
class ApplicationMetadataRepository(
    private val additionalPackageMetadataDao: AdditionalPackageMetadataDao,
    private val tagRepository: TagRepository,
    private val scope: CoroutineScope,
) {
    private val topAppsVisibilityVersionState = MutableStateFlow(0L)

    /**
     * Top-apps visibility version, bumped when the visibility metadata changes; the ranking score
     * query filters the packages hidden from top, so its cached read has to follow it.
     */
    val topAppsVisibilityVersion: StateFlow<Long> = topAppsVisibilityVersionState

    fun toggleVisibility(application: Application, visibility: ApplicationVisibility) {
        scope.launch(Dispatchers.IO) {
            additionalPackageMetadataDao.upsert(
                UpdateVisibility(
                    packageName = application.packageName,
                    application.appInfo?.user.hashCode(),
                    hideFrom = when (visibility) {
                        ApplicationVisibility.VISIBLE -> null
                        ApplicationVisibility.HIDDEN_FROM_FILTERED -> HiddenPackageType.FILTERED
                        ApplicationVisibility.HIDDEN_FROM_TOP -> HiddenPackageType.TOP
                    }
                )
            )
            topAppsVisibilityVersionState.update { it + 1 }
        }
    }

    fun setAlias(application: Application, alias: String) {
        scope.launch(Dispatchers.IO) {
            additionalPackageMetadataDao.upsert(
                UpdateAlias(
                    packageName = application.packageName,
                    application.appInfo?.user.hashCode(),
                    alias = alias
                )
            )
        }
    }

    fun setTags(application: Application, tags: Set<String>) {
        scope.launch(Dispatchers.IO) {
            tagRepository.setTags(
                application.packageName,
                application.appInfo?.user?.hashCode() ?: 0,
                tags
            )
        }
    }
}
