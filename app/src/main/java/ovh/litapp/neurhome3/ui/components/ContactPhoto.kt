package ovh.litapp.neurhome3.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ovh.litapp.neurhome3.application.NeurhomeApplication

/**
 * The contact photo a row should show, decoded only when the row is composed.
 *
 * Contact rows intentionally carry no bitmap ([ovh.litapp.neurhome3.data.Application.photoUri]):
 * a full address book is far too large to decode eagerly on every list refresh. This resolves the
 * URI off the main thread through the process-wide cache and returns the cached photo immediately
 * when a previous row already asked for it. A null URI, an unreadable photo, or a composition
 * without the real application (a preview, for instance) all fall back to the caller's placeholder.
 */
@Composable
internal fun rememberContactPhoto(photoUri: String?): Drawable? {
    if (photoUri == null) return null
    val context = LocalContext.current
    val photos = remember(context) {
        (context.applicationContext as? NeurhomeApplication)?.contactPhotos
    }
    val photo by produceState(
        initialValue = photos?.cached(photoUri),
        photoUri,
        photos,
    ) {
        value = photos?.load(photoUri)
    }
    return photo
}
