package ovh.litapp.neurhome3.data

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the row key the list and remembered row state hang off: unique per package + profile +
 * launcher component, stable across the fields that change while a row is displayed, and built once
 * per row because a list pass reads it several times.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ApplicationStableListKeyTest {
    @Test
    fun theKeyIsBuiltOncePerRow() {
        val app = Application(label = "Mail", packageName = "com.example.mail", icon = null)

        assertSame(app.stableListKey(), app.stableListKey())
    }

    @Test
    fun theKeyIgnoresScoreTagsAliasAndIcon() {
        val app = Application(label = "Mail", packageName = "com.example.mail", icon = null)

        val rescored = app.copy(score = 9.0, tags = listOf("Work"), alias = "Post", icon = Any())

        assertEquals(app.stableListKey(), rescored.stableListKey())
    }

    @Test
    fun theKeySeparatesPackages() {
        val mail = Application(label = "Mail", packageName = "com.example.mail", icon = null)
        val chat = Application(label = "Mail", packageName = "com.example.chat", icon = null)

        assertNotEquals(mail.stableListKey(), chat.stableListKey())
    }

    @Test
    fun theKeySeparatesContactRowsThatShareTheDialerPackage() {
        val home = contactRow("1234")
        val office = contactRow("5678")

        assertNotEquals(home.stableListKey(), office.stableListKey())
        assertEquals("tel:1234", home.intent?.dataString)
    }

    private fun contactRow(phoneNumber: String) = Application(
        label = "Contact",
        packageName = "com.google.android.dialer",
        icon = null,
        intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", phoneNumber, null)),
    )
}
