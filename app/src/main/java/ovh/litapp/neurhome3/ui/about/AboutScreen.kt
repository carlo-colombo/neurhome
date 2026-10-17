// SPDX-FileCopyrightText: 2023-2026 Carlo Colombo
// SPDX-License-Identifier: GPL-3.0-or-later
package ovh.litapp.neurhome3.ui.about

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ovh.litapp.neurhome3.BuildConfig

const val AboutScreenTestTag = "about-screen"
const val AboutLicenseToggleTestTag = "about-license-toggle"
const val AboutThirdPartyToggleTestTag = "about-third-party-toggle"
const val AboutSourceButtonTestTag = "about-source-button"

/** Assets staged by the `stageLicenseAssets` task from the repository root files. */
private const val LICENSE_ASSET = "licenses/LICENSE"
private const val THIRD_PARTY_ASSET = "licenses/THIRD_PARTY_LICENSES.md"

/**
 * States the app's own license and copyright, shows the full license and third-party notices
 * packaged into the APK, and links to the Corresponding Source for this build.
 *
 * The texts are read from `assets/licenses/`, which is where `stageLicenseAssets` copies the
 * repository's `LICENSE` and `THIRD_PARTY_LICENSES.md`. That keeps the displayed text identical to
 * the files a redistributor receives, with no second copy to maintain.
 */
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val licenseText by produceState(initialValue = "", LICENSE_ASSET) {
        value = readAsset(context, LICENSE_ASSET)
    }
    val thirdPartyText by produceState(initialValue = "", THIRD_PARTY_ASSET) {
        value = readAsset(context, THIRD_PARTY_ASSET)
    }
    var showLicense by rememberSaveable { mutableStateOf(false) }
    var showThirdParty by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(AboutScreenTestTag)
    ) {
        Text("Neurhome", style = MaterialTheme.typography.headlineMedium)
        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Text("Copyright (C) 2023-2026 Carlo Colombo")
        Text(
            "Licensed under the GNU General Public License, version 3 or later " +
                "(${BuildConfig.LICENSE_IDENTIFIER})."
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "This program is distributed in the hope that it will be useful, but WITHOUT ANY " +
                "WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A " +
                "PARTICULAR PURPOSE. See the GNU General Public License for more details.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = { showLicense = !showLicense },
            modifier = Modifier.testTag(AboutLicenseToggleTestTag)
        ) {
            Text(if (showLicense) "Hide license" else "Show license")
        }
        if (showLicense) LicenseText(licenseText)
        TextButton(
            onClick = { showThirdParty = !showThirdParty },
            modifier = Modifier.testTag(AboutThirdPartyToggleTestTag)
        ) {
            Text(if (showThirdParty) "Hide third-party licenses" else "Show third-party licenses")
        }
        if (showThirdParty) LicenseText(thirdPartyText)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, BuildConfig.SOURCE_URL.toUri()))
            },
            modifier = Modifier.testTag(AboutSourceButtonTestTag)
        ) {
            Text("Source code")
        }
    }
}

@Composable
private fun LicenseText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        modifier = Modifier.padding(top = 8.dp)
    )
}

/** Reads a packaged license asset, degrading to an empty string rather than crashing the screen. */
private suspend fun readAsset(context: Context, name: String): String = withContext(Dispatchers.IO) {
    runCatching {
        context.assets.open(name).bufferedReader().use { it.readText() }
    }.getOrDefault("")
}
