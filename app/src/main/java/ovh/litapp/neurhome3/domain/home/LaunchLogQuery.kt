package ovh.litapp.neurhome3.domain.home

/**
 * Query recorded with an app launch. An explicitly passed query wins; otherwise the launch is logged
 * with the home filter's current query, which is what the home search keys build up.
 */
internal fun launchLogQuery(explicitQuery: String?, homeQuery: List<String>): String =
    explicitQuery ?: homeQuery.joinToString("")
