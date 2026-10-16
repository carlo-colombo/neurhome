package ovh.litapp.neurhome3.data.repositories

import android.app.Application
import androidx.room.Room
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.models.ApplicationLogEntry

/**
 * Runs [LogRetention.pruneIfDue] against a real in-memory Room database so the one-time `ANALYZE`
 * of the maintenance pass is covered: it has to run, it has to run at most once per app version,
 * and it has to record statistics the planner can use.
 *
 * `ANALYZE` is proven by its output, `sqlite_stat1`, rather than by re-planning the ranking SQL
 * here: the interesting plan difference needs a table large enough for SQLite's estimates to
 * matter, and the plans themselves are measured separately.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LogRetentionAnalyzeTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun analyzeRunsAtMostOncePerAppVersion() = runTest {
        database.applicationLogEntryDao().insert(launch("com.example.mail"))

        retention(appVersionCode = 300).pruneIfDue()
        assertTrue("the first pass of an app version must record statistics", statisticsTableExists())
        assertTrue(statisticsRows() > 0)
        assertEquals(
            "300",
            database.settingDao().getOnce(DATABASE_ANALYZE_LAST_VERSION_SETTING)?.value,
        )

        // Dropping the statistics stands in for them going stale: only a new app version may
        // rebuild them, so a second pass of the same version must leave the table absent.
        dropStatistics()
        retention(appVersionCode = 300).pruneIfDue()
        assertFalse(statisticsTableExists())

        retention(appVersionCode = 301).pruneIfDue()
        assertTrue(statisticsTableExists())
    }

    @Test
    fun aNewAppVersionAnalyzesEvenWhenThePruningAlreadyRanToday() = runTest {
        retention(appVersionCode = 300).pruneIfDue()
        dropStatistics()
        database.applicationLogEntryDao()
            .insert(launch("com.example.expired", timestamp = "2020-01-01T00:00:00"))

        retention(appVersionCode = 301).pruneIfDue()

        assertTrue(statisticsTableExists())
        assertEquals(
            "301",
            database.settingDao().getOnce(DATABASE_ANALYZE_LAST_VERSION_SETTING)?.value,
        )
        // The once-a-day pruning half is skipped, so the expired row it would have deleted is
        // still there: the statistics rebuild is not gated on the daily pass.
        assertTrue(
            database.applicationLogEntryDao().all().any { it.packageName == "com.example.expired" }
        )
    }

    private fun retention(appVersionCode: Int) = LogRetention(
        logEntryDao = database.applicationLogEntryDao(),
        settingDao = database.settingDao(),
        database = database,
        appVersionCode = appVersionCode,
        now = { LocalDateTime.of(2026, 9, 30, 12, 0) },
    )

    private fun launch(packageName: String, timestamp: String = "2026-09-29T10:00:00") =
        ApplicationLogEntry(
            packageName = packageName,
            timestamp = timestamp,
            wifi = null,
            latitude = null,
            longitude = null,
            geohash = null,
            user = 0,
        )

    private fun statisticsTableExists(): Boolean = database.openHelper.readableDatabase
        .query("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'sqlite_stat1'")
        .use { it.moveToFirst() && it.getInt(0) > 0 }

    private fun statisticsRows(): Int = database.openHelper.readableDatabase
        .query("SELECT count(*) FROM sqlite_stat1")
        .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun dropStatistics() = database.openHelper.writableDatabase
        .execSQL("DROP TABLE sqlite_stat1")
}
