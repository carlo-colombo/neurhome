package ovh.litapp.neurhome3.data.repositories

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.models.ApplicationTag

/**
 * Pins the single, locale-independent order shared by every application and tag sort path.
 *
 * Two label pairs a divergent sort would flip:
 * - plain "Istanbul" vs "izmir" flips under a Turkish default locale, where a default-locale
 *   `lowercase()` maps "I" to the dotless "ı", which sorts after "i".
 * - U+0130 "İstanbul" vs "izmir" flips under any locale when sorting lower-cased strings,
 *   because `"İ".lowercase()` expands to "i" followed by a combining dot above, which sorts
 *   after "z".
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
        return filterApplicationsForTag(applications, tagName = null).map { it.label }
    }

    private fun tagNamesInOrder(tagNames: List<String>): List<String> {
        val assignments = tagNames.map { ApplicationTag(packageName = "pkg", profile = 0, tagName = it) }
        return tagNamesByApplication(assignments)["pkg" to 0].orEmpty()
    }
}
