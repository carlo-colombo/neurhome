package ovh.litapp.neurhome3.data.repositories

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.ApplicationVisibility
import ovh.litapp.neurhome3.data.models.AdditionalPackageMetadata
import ovh.litapp.neurhome3.data.models.HiddenPackageType

/**
 * Pins [ApplicationMetadataIndex] as the one place a row built outside `LauncherAppsSource` gets
 * its visibility, alias and tags. It is what makes a top-apps row and the same application in the
 * All Applications screen agree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ApplicationMetadataIndexTest {
    @Test
    fun eachStoredHideReportsItsOwnVisibility() {
        listOf(
            HiddenPackageType.FILTERED to ApplicationVisibility.HIDDEN_FROM_FILTERED,
            HiddenPackageType.TOP to ApplicationVisibility.HIDDEN_FROM_TOP,
        ).forEach { (hideFrom, visibility) ->
            val index = indexOf(
                AdditionalPackageMetadata("com.example.mail", user = 0, hideFrom = hideFrom, alias = null)
            )

            assertEquals(visibility, index.decorate(app("com.example.mail")).visibility)
        }
    }

    /**
     * A contact row has no launcher activity, so [Application.profile] is 0 - which is the key its
     * metadata is written under. Before the index was applied to contact rows, hiding a contact
     * from filtering was stored and then never read, and the contact stayed in the home search.
     */
    @Test
    fun anIntentOnlyRowReadsTheMetadataOfItsCallUri() {
        val callUri = Uri.fromParts("tel", "+3912345678", null).toString()
        val index = indexOf(
            AdditionalPackageMetadata(callUri, user = 0, hideFrom = HiddenPackageType.FILTERED, alias = null)
        )
        val contact = Application(
            label = "Ada",
            packageName = callUri,
            icon = null,
            intent = Intent(Intent.ACTION_CALL, Uri.parse(callUri)),
        )

        assertEquals(ApplicationVisibility.HIDDEN_FROM_FILTERED, index.decorate(contact).visibility)
    }

    @Test
    fun anotherProfilesMetadataDoesNotReachTheRow() {
        val index = indexOf(
            AdditionalPackageMetadata("com.example.mail", user = 10, hideFrom = HiddenPackageType.TOP, alias = "Work")
        )

        val decorated = index.decorate(app("com.example.mail"))

        assertEquals(ApplicationVisibility.VISIBLE, decorated.visibility)
        assertEquals("", decorated.alias)
    }

    @Test
    fun aliasIsAppliedAndTheTagOrderIsPreserved() {
        val index = ApplicationMetadataIndex(
            metadataByApplication = mapOf(
                ("com.example.mail" to 0) to
                    AdditionalPackageMetadata("com.example.mail", user = 0, hideFrom = null, alias = "Mail alias")
            ),
            tagsByApplication = mapOf(("com.example.mail" to 0) to listOf("Work", "Tools")),
        )

        val decorated = index.decorate(app("com.example.mail"))

        assertEquals("Mail alias", decorated.alias)
        assertEquals(listOf("Work", "Tools"), decorated.tags)
        assertEquals(ApplicationVisibility.VISIBLE, decorated.visibility)
    }

    /**
     * The empty index is the state before Room emitted: a row that carries no metadata must come
     * back as the same instance, not a copy.
     */
    @Test
    fun theEmptyIndexLeavesARowUntouched() {
        val row = app("com.example.mail")

        assertSame(row, ApplicationMetadataIndex.EMPTY.decorate(row))
    }

    private fun indexOf(vararg metadata: AdditionalPackageMetadata) = ApplicationMetadataIndex(
        metadataByApplication = metadata.associateBy { it.packageName to it.user },
    )

    private fun app(packageName: String) = Application(
        label = packageName,
        packageName = packageName,
        icon = null,
        alias = "",
    )
}
