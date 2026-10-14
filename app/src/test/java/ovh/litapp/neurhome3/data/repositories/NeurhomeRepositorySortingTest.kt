package ovh.litapp.neurhome3.data.repositories

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.models.ApplicationTag

/**
 * Pins the single label order shared by every application and tag sort path
 * ([String.CASE_INSENSITIVE_ORDER], via [sortedByLabelCaseInsensitive] for applications).
 *
 * The tests run under a Turkish default locale to pin that the order is locale-independent: a
 * default-locale `lowercase()` maps "I" to the dotless "ı", flipping plain "Istanbul" vs
 * "izmir".
 *
 * The U+0130 pair ("İstanbul" vs "izmir") pins the other divergence a lower-cased sort would
 * introduce: `"İ".lowercase()` expands to "i" followed by a combining dot above (U+0307), which
 * sorts after "z", while [String.CASE_INSENSITIVE_ORDER] folds "İ" to "i" and keeps "İstanbul"
 * first. That is Unicode case-expansion rather than locale sensitivity: Kotlin's no-arg
 * `String.lowercase()` is invariant-locale, so the Turkish case only bites an explicit
 * `lowercase(Locale.getDefault())`.
 */
class NeurhomeRepositorySortingTest {
    private val originalLocale = Locale.getDefault()

    @Before
    fun useTurkishDefaultLocale() {
        Locale.setDefault(Locale.forLanguageTag("tr"))
    }

    @After
    fun restoreDefaultLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun applicationLabelsSortByTheSharedLocaleIndependentComparator() {
        assertEquals(listOf("Istanbul", "izmir"), labelsInOrder(listOf("izmir", "Istanbul")))
        assertEquals(listOf("\u0130stanbul", "izmir"), labelsInOrder(listOf("izmir", "\u0130stanbul")))
    }

    @Test
    fun tagNamesForAnApplicationSortByTheSharedLocaleIndependentComparator() {
        assertEquals(listOf("Istanbul", "izmir"), tagNamesInOrder(listOf("izmir", "Istanbul")))
        assertEquals(listOf("\u0130stanbul", "izmir"), tagNamesInOrder(listOf("izmir", "\u0130stanbul")))
    }

    private fun labelsInOrder(labels: List<String>): List<String> {
        val applications = labels.map { Application(label = it, packageName = it, icon = null) }
        return applications.sortedByLabelCaseInsensitive().map { it.label }
    }

    private fun tagNamesInOrder(tagNames: List<String>): List<String> {
        val assignments = tagNames.map { ApplicationTag(packageName = "pkg", profile = 0, tagName = it) }
        return tagNamesByApplication(assignments)["pkg" to 0].orEmpty()
    }
}
