package ovh.litapp.neurhome3

import android.graphics.Paint
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * API 37 changed `LauncherActivityInfo.isVisible` to keep a label only when
 * [Paint.hasGlyph] finds a glyph for every character in it. Robolectric has no real font stack, so
 * the native `hasGlyph` reports nothing and every launcher label silently degrades to the package
 * name - a divergence a device does not have. Report glyphs as present whenever the string is
 * non-empty, which is what a device with any font does for Latin labels, so label resolution behaves
 * as it does on-device.
 *
 * Registered for the whole JVM suite through `robolectric.properties`, because the degraded label is
 * reachable from any launcher-activity label read, not just from the tests that assert one.
 */
@Implements(Paint::class)
class ShadowPaintHasGlyph {
    @Implementation
    fun hasGlyph(string: String?): Boolean = !string.isNullOrEmpty()
}
