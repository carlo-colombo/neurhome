package ovh.litapp.neurhome3.ui.settings

import ovh.litapp.neurhome3.data.repositories.HomeAppSelection

/**
 * Requirements for the learned Home ranking to actually run. Selecting the
 * learned ranking never enables logging by itself: the loggers stay separate
 * privacy opt-ins, and a missing requirement only makes the learned ranking
 * inactive, leaving the classic ranking in charge.
 *
 * Wi-Fi connectivity is deliberately not a requirement: a known disconnected
 * state is valid model input.
 */
data class LearnedRankingPrerequisites(
    val wifiLogging: Boolean,
    val positionLogging: Boolean,
    val locationPermissionGranted: Boolean
) {
    val isSatisfied: Boolean
        get() = wifiLogging && positionLogging && locationPermissionGranted

    fun missingRequirements(): List<String> = buildList {
        if (!wifiLogging) add("Wi-Fi logging is off")
        if (!positionLogging) add("position logging is off")
        if (!locationPermissionGranted) add("location permission is not granted")
    }

    /**
     * Explains whether the learned ranking is running. When requirements are
     * missing the choice is still stored, so the message states that Home keeps
     * using the classic ranking until they are met.
     */
    fun explain(selection: HomeAppSelection): String? {
        if (isSatisfied) return null
        val requirements = missingRequirements().joinToString(", ")
        return if (selection == HomeAppSelection.LEARNED) {
            "Learned ranking is inactive. It needs $requirements. " +
                "Until then Home keeps using the classic ranking."
        } else {
            "The learned ranking needs $requirements before it can be used."
        }
    }
}
