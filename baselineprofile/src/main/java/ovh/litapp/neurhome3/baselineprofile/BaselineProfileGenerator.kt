package ovh.litapp.neurhome3.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

/**
 * Records the Home cold-start journey for the prod build of the app.
 *
 * This drives a real install of the app under test, so it has to run on a device that
 * represents the devices users run the app on: an emulator-generated profile encodes the
 * emulator's own ART/JIT behaviour and can be worse than no profile at all.
 *
 * See `baselineprofile/README.md` for the command that runs this.
 */
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun coldStart() {
        baselineProfileRule.collect(
            packageName = PROD_APPLICATION_ID,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            // Let the Home dashboard finish its first composition so its frames are part
            // of the profile rather than of an unfinished startup.
            device.waitForIdle()
        }
    }
}

/**
 * The prod flavor's application id. Prod carries no suffix, while dev appends
 * `.dev.<hostname>` (see `app/build.gradle.kts`), so only prod can be addressed by a
 * constant; a dev run would have to be given that machine's suffixed id instead.
 */
private const val PROD_APPLICATION_ID = "ovh.litapp.neurhome3"
