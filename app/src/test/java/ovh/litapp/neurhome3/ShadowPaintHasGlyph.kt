package ovh.litapp.neurhome3

import android.graphics.Paint
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.GraphicsShadowPicker
import org.robolectric.shadows.ShadowNativePaint
import org.robolectric.shadows.ShadowPaint

/**
 * API 37 changed `LauncherActivityInfo.isVisible` to keep a label only when
 * [Paint.hasGlyph] finds a glyph for every character in it. Robolectric has no real font stack, so
 * the legacy `hasGlyph` reports nothing and every launcher label silently degrades to the package
 * name - a divergence a device does not have. Report glyphs as present whenever the string is
 * non-empty, which is what a device with any font does for Latin labels, so label resolution behaves
 * as it does on-device.
 *
 * Registered for the whole JVM suite through `robolectric.properties`, because the degraded label is
 * reachable from any launcher-activity label read, not just from the tests that assert one.
 *
 * The `shadowPicker` is load-bearing. A plain `@Implements(Paint::class)` here *replaces* whichever
 * Paint shadow the graphics mode would otherwise use, and under `@GraphicsMode(NATIVE)` that means
 * `ShadowNativePaint` never applies: `Paint` then carries no native SkPaint (`mNativePaint == 0`)
 * and the first draw call that takes a Paint segfaults the test JVM inside `libandroid_runtime.so`.
 * Picking per graphics mode keeps [hasGlyph] answering while leaving the mode's own Paint shadow in
 * charge of everything else.
 *
 * The native branch picks [ShadowNativePaint] itself rather than a subclass of it, and that is not
 * an oversight: Robolectric resolves a shadow method by walking the shadow class's superclasses, but
 * only through classes that carry `@Implements` of their own. `ShadowNativePaint` does not, so a
 * subclass declaring one method silently loses every native implementation it inherits - including
 * `nInit` - and lands back on the null-native-Paint crash above. Under native graphics
 * `ShadowNativePaint`'s own `nHasGlyph` answers through the real font stack anyway, which is why no
 * override is needed on that branch.
 */
@Implements(value = Paint::class, shadowPicker = ShadowPaintHasGlyph.Picker::class)
class ShadowPaintHasGlyph {
    class Picker : GraphicsShadowPicker<Any>(
        ShadowLegacyPaintHasGlyph::class.java,
        ShadowNativePaint::class.java,
    )
}

/** The legacy-graphics branch: every [ShadowPaint] implementation plus the `hasGlyph` answer. */
@Implements(Paint::class)
class ShadowLegacyPaintHasGlyph : ShadowPaint() {
    @Implementation
    fun hasGlyph(string: String?): Boolean = !string.isNullOrEmpty()
}
