// SPDX-FileCopyrightText: 2023-2026 Carlo Colombo
// SPDX-License-Identifier: GPL-3.0-or-later
package ovh.litapp.neurhome3

import android.app.Application
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins that the app's own license and the third-party notices actually reach the merged assets.
 *
 * The files are staged by the `stageLicenseAssets` task from the repository root rather than
 * committed under `app/src/main/assets`, so a broken `assets.srcDir` wiring (or a file renamed at
 * the root) would ship an APK with no license while every file still exists in the repository.
 * Reading through `RuntimeEnvironment`'s merged assets is the cheapest place to catch that: a JVM
 * unit test sees the app variant's merged assets, so this fails in the same task the gate runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LicenseAssetsTest {
    @Test
    fun theGnuGeneralPublicLicenseIsPackaged() {
        val license = readAsset("licenses/LICENSE")

        assertTrue(
            "the packaged license must be the GPLv3 text",
            license.contains("GNU GENERAL PUBLIC LICENSE")
        )
        assertTrue(
            "the packaged license must be version 3",
            license.contains("Version 3, 29 June 2007")
        )
    }

    @Test
    fun theCopyrightNoticeIsPackaged() {
        val notice = readAsset("licenses/NOTICE")

        assertTrue(
            "the notice must carry the copyright holder",
            notice.contains("Copyright (C) 2023-2026 Carlo Colombo")
        )
        assertTrue(
            "the notice must state the SPDX identifier",
            notice.contains("GPL-3.0-or-later")
        )
    }

    @Test
    fun theThirdPartyNoticesArePackaged() {
        val notices = readAsset("licenses/THIRD_PARTY_LICENSES.md")

        assertTrue(
            "the notices must include the Apache-2.0 text",
            notices.contains("Apache License") && notices.contains("Version 2.0, January 2004")
        )
        assertTrue(
            "the notices must include the MIT text for slf4j",
            notices.contains("org.slf4j:slf4j-api")
        )
        assertTrue(
            "the notices must list the Compose artifacts the app links",
            notices.contains("androidx.compose.ui:ui-android")
        )
    }

    /**
     * The avatar renderer is a port of DiceBear's MIT core, so its notice is an obligation, not the
     * CC0 courtesy the style definitions are under. Nothing else in the notices file would carry it:
     * it is not a resolved dependency and no POM declares it.
     */
    @Test
    fun theThirdPartyNoticesCreditThePortedAvatarRenderer() {
        val notices = readAsset("licenses/THIRD_PARTY_LICENSES.md")

        assertTrue(
            "the notices must name the ported renderer",
            notices.contains("dicebear/dicebear")
        )
        assertTrue(
            "the notices must carry DiceBear's MIT copyright line",
            notices.contains("Copyright (c) 2026 Florian K")
        )
        assertTrue(
            "the notices must name the vendored style package",
            notices.contains("@dicebear/styles")
        )
        assertTrue(
            "the notices must point at the per-style licenses",
            notices.contains("assets/dicebear/LICENSES.md")
        )
    }

    /**
     * Every vendored style must reach the device with its own license text attached.
     *
     * The definitions carry their license in `meta.license.text`, and that text is what
     * `assets/dicebear/LICENSES.md` reproduces. This guards the pairing in both directions: a style
     * added without its notice, and a notice left behind by a style that was removed, both fail.
     */
    @Test
    fun everyVendoredAvatarStyleIsCoveredByThePackagedLicenses() {
        val definitions = RuntimeEnvironment.getApplication().assets.list("dicebear").orEmpty()
            .filter { it.endsWith(".json") }
            .sorted()
        val licenses = readAsset("dicebear/LICENSES.md")

        assertTrue("the vendored style definitions must ship", definitions.isNotEmpty())
        val uncovered = definitions.filterNot { licenses.contains("## $it") }
        assertTrue(
            "these styles ship with no entry in dicebear/LICENSES.md: $uncovered",
            uncovered.isEmpty()
        )
    }

    private fun readAsset(name: String): String =
        RuntimeEnvironment.getApplication().assets.open(name).bufferedReader().use { it.readText() }
}
