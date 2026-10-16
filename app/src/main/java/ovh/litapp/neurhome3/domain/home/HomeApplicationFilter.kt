package ovh.litapp.neurhome3.domain.home

import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.ApplicationVisibility

/**
 * How many applications the Home dashboard shows at once: the top apps and the filter suggestions.
 */
internal const val MAX_HOME_APPLICATIONS = 6

/**
 * Applies the home search keys: an empty query yields no suggestions, otherwise the apps whose
 * label or alias matches the `\b(my)?<keys>` pattern, best score first, capped at
 * [MAX_HOME_APPLICATIONS] and returned in display order. Apps hidden from filtering are excluded.
 */
internal fun filterApplicationsForQuery(
    allApplications: List<Application>,
    query: List<String>,
): List<Application> {
    if (query.isEmpty()) return emptyList()

    val filter = homeQueryPattern(query.joinToString(""))

    return allApplications.filter {
        it.visibility != ApplicationVisibility.HIDDEN_FROM_FILTERED &&
            (filter matches it.label || filter matches it.alias)
    }.sortedBy { -it.score }.take(MAX_HOME_APPLICATIONS).reversed()
}

/**
 * The `\b(my)?<keys>` matcher for a home query. The filter re-runs whenever the application list
 * changes while the keys stay the same, so the pattern is compiled once per distinct query string
 * instead of on every call.
 */
internal fun homeQueryPattern(keys: String): Regex = HomeQueryPatternCache.patternFor(keys)

private object HomeQueryPatternCache {
    private var keys: String? = null
    private var pattern: Regex? = null

    @Synchronized
    fun patternFor(keys: String): Regex {
        val cached = pattern
        if (cached != null && keys == this.keys) return cached

        return Regex(
            ".*\\b(my)?$keys.*", RegexOption.IGNORE_CASE
        ).also {
            this.keys = keys
            this.pattern = it
        }
    }
}
