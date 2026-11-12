package ovh.litapp.neurhome3.data.repositories

import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.ApplicationVisibility
import ovh.litapp.neurhome3.data.models.AdditionalPackageMetadata
import ovh.litapp.neurhome3.data.models.HiddenPackageType

/** The visibility a stored [HiddenPackageType] means; `null` is an application with no metadata. */
internal fun HiddenPackageType?.toApplicationVisibility(): ApplicationVisibility = when (this) {
    HiddenPackageType.TOP -> ApplicationVisibility.HIDDEN_FROM_TOP
    HiddenPackageType.FILTERED -> ApplicationVisibility.HIDDEN_FROM_FILTERED
    null -> ApplicationVisibility.VISIBLE
}

/**
 * The per-application metadata that no row builder except [LauncherAppsSource] applies on its own:
 * visibility, alias and tag assignments.
 *
 * A row resolved from a package name or a call URI (the home top apps, the statistics simulation)
 * and a contact row both come out of their builders without any of it. The reads that show them
 * decorate the resolved row here, so the same application cannot report a different visibility,
 * alias or tag set depending on which screen opened its management panel. Keyed by
 * [Application.profile], which is the profile the writes used.
 *
 * [EMPTY] is the state before the Room reads have emitted; it leaves a row untouched.
 */
internal data class ApplicationMetadataIndex(
    private val metadataByApplication: Map<Pair<String, Int>, AdditionalPackageMetadata> = emptyMap(),
    private val tagsByApplication: Map<Pair<String, Int>, List<String>> = emptyMap(),
) {
    fun decorate(application: Application): Application {
        val key = application.packageName to application.profile
        val metadata = metadataByApplication[key]
        val visibility = metadata?.hideFrom.toApplicationVisibility()
        val alias = metadata?.alias ?: ""
        val tags = tagsByApplication[key].orEmpty()
        if (application.visibility == visibility && application.alias == alias && application.tags == tags) {
            return application
        }
        return application.copy(visibility = visibility, alias = alias, tags = tags)
    }

    companion object {
        val EMPTY = ApplicationMetadataIndex()
    }
}
