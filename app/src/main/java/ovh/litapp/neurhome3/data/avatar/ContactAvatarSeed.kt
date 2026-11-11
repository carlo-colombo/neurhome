package ovh.litapp.neurhome3.data.avatar

import android.content.Intent
import ovh.litapp.neurhome3.data.Application
import java.util.Locale

/**
 * Whether this row is a contact row: it launches a call rather than an activity.
 *
 * The action is what decides, not the presence of a name, so a contact saved with only a number
 * still gets an avatar. A launcher row carries no intent at all and never takes the avatar path.
 */
val Application.isContactRow: Boolean
    get() = intent?.action == Intent.ACTION_CALL

/**
 * The seed a contact row's generated avatar is drawn from: the contact's own name, or its call URI
 * when the contact has no name at all, or null for a row that is not a contact row.
 *
 * Every number of one contact seeds the same avatar, because a contact with three numbers is three
 * rows that carry the same name. Falling back to the call URI cannot split a contact across
 * avatars either: it is the only case where a number reaches the hash, and there it is the only
 * name the row has.
 */
fun contactAvatarSeed(row: Application): String? {
    if (!row.isContactRow) return null
    return contactNameSeed(row.contactName) ?: row.packageName
}

/**
 * [name] normalised into a seed, or null when there is no name to hash.
 *
 * Case and surrounding whitespace are the two ways one contact's name reaches the app spelled
 * differently (a provider may capitalise it, a sync may pad it), so neither may change the picture.
 * [Locale.ROOT] is deliberate: a Turkish locale lowercases "I" to a dotless "ı", which would draw a
 * different avatar on a Turkish device than on every other one.
 */
fun contactNameSeed(name: String?): String? =
    name?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
