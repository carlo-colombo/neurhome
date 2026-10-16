package ovh.litapp.neurhome3

import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import ovh.litapp.neurhome3.data.Application
import ovh.litapp.neurhome3.data.dao.ApplicationLogEntryDao

/**
 * Replaces [ApplicationService.toApplication] for JVM tests. The real lookup needs the launcher
 * binder, a launch intent and a resolvable launcher activity, none of which exist off-device, so
 * the repositories built on top of it (the favourite slots, the Home ranking resolver) are
 * otherwise only reachable on an instrumented device.
 *
 * A test opts in with `@Config(shadows = [ShadowApplicationService::class])`, registers the
 * packages it wants to resolve in [resolved], and can then assert on [lookups], which records the
 * package/profile/score row the production code asked for.
 */
@Implements(ApplicationService::class)
class ShadowApplicationService {
    @Implementation
    fun toApplication(packageCount: ApplicationLogEntryDao.PackageCount): Application? {
        lookups += packageCount
        return resolved[packageCount.packageName]
    }

    companion object {
        /** Packages the fake lookup resolves; an unlisted package resolves to `null`. */
        val resolved: MutableMap<String, Application> = mutableMapOf()

        /** Every lookup the production code performed, in call order. */
        val lookups: MutableList<ApplicationLogEntryDao.PackageCount> = mutableListOf()

        fun reset() {
            resolved.clear()
            lookups.clear()
        }
    }
}
