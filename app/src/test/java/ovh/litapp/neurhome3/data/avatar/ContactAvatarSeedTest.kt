package ovh.litapp.neurhome3.data.avatar

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ovh.litapp.neurhome3.data.Application

/**
 * Pins the two answers the generated avatar depends on: which rows take one at all
 * ([isContactRow]) and what a row hashes into a picture ([contactAvatarSeed]).
 *
 * The rule that has to hold is "one contact, one avatar": every number of a contact is its own
 * row, so a seed derived from anything row-specific (the label, the call URI, the number) would
 * draw a different avatar for each of them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ContactAvatarSeedTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun caseAndSurroundingSpaceInOneNameSeedOneValue() {
        assertEquals("ada", contactNameSeed("Ada"))
        assertEquals("ada", contactNameSeed("  Ada  "))
        assertEquals("ada", contactNameSeed("ADA"))
        assertEquals("ada", contactNameSeed("\tADA\n"))
        assertEquals("ada lovelace", contactNameSeed(" Ada Lovelace "))
    }

    @Test
    fun aBlankOrMissingNameHasNoSeed() {
        assertNull(contactNameSeed(null))
        assertNull(contactNameSeed(""))
        assertNull(contactNameSeed("   "))
    }

    /**
     * The name is lowercased with [java.util.Locale.ROOT]: a Turkish device lowercases "I" to a
     * dotless "ı", which would otherwise draw a different avatar there than everywhere else.
     */
    @Test
    fun aTurkishDeviceSeedsTheSameValue() {
        val default = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
            assertEquals("ivan", contactNameSeed("IVAN"))
        } finally {
            java.util.Locale.setDefault(default)
        }
    }

    /**
     * FNV-1a hashes UTF-16 code units, so a name outside the Basic Multilingual Plane has to
     * reach it with both of its surrogate units intact - the seed is the name, not a normalised
     * form of it.
     */
    @Test
    fun nonLatinAndEmojiNamesSeedThemselves() {
        assertEquals("àda", contactNameSeed("  ÀDA "))
        assertEquals("日本語", contactNameSeed("日本語"))

        val withEmoji = requireNotNull(contactNameSeed("Ada 🎲"))
        assertEquals("ada 🎲", withEmoji)
        assertEquals(6, withEmoji.length)
        assertEquals("ada 🎲", contactNameSeed("ADA 🎲"))
    }

    @Test
    fun namesDifferingOnlyInCaseDrawTheSameAvatar() {
        val style = DiceBearStyle.SHADOWS
        val padded = requireNotNull(style.resolve(context, requireNotNull(contactNameSeed("  Ada  "))))
        val shouted = requireNotNull(style.resolve(context, requireNotNull(contactNameSeed("ADA"))))

        assertEquals(padded, shouted)
        assertNotEquals(
            "a different name must still draw a different avatar",
            padded,
            requireNotNull(style.resolve(context, requireNotNull(contactNameSeed("Bob")))),
        )
    }

    @Test
    fun everyNumberOfOneContactSeedsTheSameAvatar() {
        val mobile = contactRow(name = "Ada", number = "+39111")
        val work = contactRow(name = "Ada", number = "+39222")

        assertEquals("ada", contactAvatarSeed(mobile))
        assertEquals(contactAvatarSeed(mobile), contactAvatarSeed(work))
        assertNotEquals(mobile.packageName, work.packageName)
    }

    @Test
    fun aContactWithNoNameSeedsItsCallUri() {
        val nameless = contactRow(name = null, number = "+39111")

        assertNull(nameless.contactName)
        assertEquals(nameless.packageName, contactAvatarSeed(nameless))
        assertTrue(contactAvatarSeed(nameless)?.startsWith("tel:") == true)
    }

    @Test
    fun aRowThatIsNotAContactRowSeedsNothing() {
        val launcherRow = Application(label = "Maps", packageName = "com.example.maps", icon = null)
        val otherIntentRow = Application(
            label = "Browse",
            packageName = "https://example.com",
            icon = null,
            intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
        )

        assertNull(contactAvatarSeed(launcherRow))
        assertNull(contactAvatarSeed(otherIntentRow))
        assertTrue(launcherRow.intent == null)
    }

    private fun contactRow(name: String?, number: String): Application {
        val callUri = Uri.fromParts("tel", number, null)
        return Application(
            label = name ?: number,
            packageName = callUri.toString(),
            icon = null,
            intent = Intent(Intent.ACTION_CALL, callUri),
            contactName = name,
        )
    }
}
