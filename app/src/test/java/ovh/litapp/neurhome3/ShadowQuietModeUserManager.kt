package ovh.litapp.neurhome3

import android.os.UserHandle
import android.os.UserManager
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Answers [UserManager.isQuietModeEnabled] from test state for the tests that execute the real
 * [ApplicationService] body.
 *
 * Robolectric's own `ShadowUserManager` derives quiet mode from a registered `UserInfo`'s flags,
 * which cannot be driven reliably for this lookup: the flags a test registers and the flags
 * `isQuietModeEnabled` reads do not line up, so the profile state the service caches would be built
 * from values the test never set. This shadow therefore owns the answer and, through [reads],
 * records how often the platform was asked - which is what makes the caching visible.
 */
@Implements(UserManager::class)
class ShadowQuietModeUserManager {
    @Implementation
    fun isQuietModeEnabled(userHandle: UserHandle): Boolean {
        reads += userHandle
        return quietModes[userHandle] ?: false
    }

    companion object {
        /** Quiet mode of each profile; a profile that is absent is not in quiet mode. */
        val quietModes: MutableMap<UserHandle, Boolean> = mutableMapOf()

        /** Every quiet-mode read, in call order. */
        val reads: MutableList<UserHandle> = mutableListOf()

        fun reset() {
            quietModes.clear()
            reads.clear()
        }
    }
}
