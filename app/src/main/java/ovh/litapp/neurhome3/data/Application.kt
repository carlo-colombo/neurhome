package ovh.litapp.neurhome3.data

import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.graphics.drawable.Drawable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.vector.ImageVector

data class Application(
    val label: String = "",
    /**
     * Row identity, and the key a launch of the row is logged under: a launcher row's package name,
     * or, for an intent-only row such as a starred contact, the call URI that launches it. The
     * launch-log ranking resolves a logged key back to its row through this string, so the two must
     * stay the same string.
     */
    val packageName: String = "",
    val icon: Drawable?,
    /**
     * The contact thumbnail a row would show, resolved lazily by the UI (`rememberContactPhoto`)
     * rather than decoded while the contact list is built: a full address book is large enough that
     * eager per-row decoding would cost thousands of thumbnail reads on every refresh. Launcher
     * rows carry their icon directly and leave this null.
     */
    val photoUri: String? = null,
    val score: Double = 0.0,
    val appInfo: LauncherActivityInfo? = null,
    val intent: Intent? = null,
    val visibility: ApplicationVisibility = ApplicationVisibility.VISIBLE,
    val alias: String = "",
    val tags: List<String> = emptyList(),
    /**
     * The contact's own name - trimmed, and null when the row has no name at all (a contact saved
     * with only a number, or a provider that reports the number itself as the name) - or null for
     * every launcher row. `ContactsDAO` is what decides that, once, for this field and for the
     * label's type suffix alike.
     *
     * The generated avatar is seeded from this and never from [label]: the label carries the
     * number's type ("Ada (Mobile)"), and a contact's three numbers are three rows that must all
     * draw the same avatar.
     */
    val contactName: String? = null,
) {
    /**
     * Whether a launch of this row can be tracked in the launch log: a launcher row carries the
     * launcher activity it is launched through, an intent-only row carries its call intent, and
     * both log under [packageName]. A row with neither cannot be launched at all, so it must not
     * claim tracking. Visibility is deliberately not part of this: hiding a row from a list does
     * not stop a launch that happens.
     */
    val canTrack: Boolean get() = appInfo != null || intent != null

    /**
     * The profile this row belongs to: the launcher activity's user, or 0 for an intent-only row.
     *
     * Every per-application metadata path keys on it - the alias/visibility/tag writes, the tag
     * lookup of the merged list, and the `user` a launch is logged under - so a launcher row and a
     * contact row have to derive the same key here that their write used. The owner profile's
     * [android.os.UserHandle] hashes to 0, which is what an intent-only row resolves to.
     */
    val profile: Int get() = appInfo?.user?.hashCode() ?: 0

    /**
     * Stable identity of a launcher row: package, profile, and launcher component (or, for an
     * intent-only row, its call URI). Row keys and remembered row state hang off it, so it must
     * stay unique per package + profile + launcher component. Built once per instance because a
     * single list pass reads it several times per row.
     */
    internal val listKey: String by lazy {
        buildString {
            append(packageName)
            append(':')
            append(profile)
            append(':')
            append(appInfo?.componentName?.flattenToString() ?: intent?.dataString ?: label)
        }
    }
}

fun Application.stableListKey(): String = listKey

enum class ApplicationVisibility(val imageVector: ImageVector, val description: String) {
    VISIBLE(
        Icons.Default.Visibility, "Visible"
    ),
    HIDDEN_FROM_FILTERED(Icons.Default.FilterListOff, "Hidden when filtering"),
    HIDDEN_FROM_TOP(
        Icons.Default.FlashOff,
        "Hidden from top apps"
    );
}
