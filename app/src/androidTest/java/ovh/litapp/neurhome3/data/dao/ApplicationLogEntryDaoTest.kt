package ovh.litapp.neurhome3.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.models.ApplicationLogEntry
import ovh.litapp.neurhome3.data.models.HiddenPackageType

/**
 * Runs the real [ApplicationLogEntryDao] ranking SQL against a fresh in-memory Room database so the
 * top-apps ordering, the `LIMIT` bound and the hidden-from-top exclusion are covered without any
 * personal database. Instrumentation-only because the ranking SQL needs the framework SQLite.
 */
@RunWith(AndroidJUnit4::class)
class ApplicationLogEntryDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ApplicationLogEntryDao
    private lateinit var metadataDao: AdditionalPackageMetadataDao

    // One snapshot for every inserted row so all launches share a timestamp inside the SQL's
    // 20-minute window around "now".
    private val now: LocalDateTime = LocalDateTime.now().withNano(0)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.applicationLogEntryDao()
        metadataDao = database.hiddenPackageDao()

        repeat(5) { dao.insert(entry("heavy")) }
        repeat(3) { dao.insert(entry("medium")) }
        dao.insert(entry("light"))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun topAppsByScoreOrdersByLaunchCountAndHonoursTheLimit() {
        assertEquals(
            listOf("heavy", "medium"),
            dao.topAppsByScore(limit = 2).map { it.packageName }
        )
        assertEquals(listOf("heavy"), dao.topAppsByScore(limit = 1).map { it.packageName })
        assertEquals(
            listOf("heavy", "medium", "light"),
            dao.topAppsByScore(limit = 10).map { it.packageName }
        )
    }

    @Test
    fun topAppsByScoreExcludesPackagesHiddenFromTop() {
        metadataDao.upsert(UpdateVisibility("heavy", user = 0, hideFrom = HiddenPackageType.TOP))

        assertEquals(
            listOf("medium", "light"),
            dao.topAppsByScore(limit = 10).map { it.packageName }
        )
    }

    @Test
    fun mostUsedAppsCountsLaunchesPerPackageAndProfile() {
        dao.insert(entry("heavy", user = 10))

        val counts = dao.mostUsedApps().associate { (it.packageName to it.user) to it.score }

        assertEquals(5.0, counts["heavy" to 0]!!, 0.0)
        assertEquals(1.0, counts["heavy" to 10]!!, 0.0)
        assertEquals(3.0, counts["medium" to 0]!!, 0.0)
    }

    @Test
    fun simulatedTopAppsHonoursTheLimitAndHidesTopHiddenPackages() {
        metadataDao.upsert(UpdateVisibility("medium", user = 0, hideFrom = HiddenPackageType.TOP))
        val dayOfWeek = now.dayOfWeek.value % 7
        val minuteOfDay = now.hour * 60 + now.minute

        val ranked = dao.simulatedTopApps(dayOfWeek, minuteOfDay, wifi = null, limit = 10)
            .map { it.packageName }
        assertEquals(listOf("heavy", "light"), ranked)
        assertEquals(
            1,
            dao.simulatedTopApps(dayOfWeek, minuteOfDay, wifi = null, limit = 1).size
        )
    }

    private fun entry(packageName: String, user: Int = 0) = ApplicationLogEntry(
        packageName = packageName,
        timestamp = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        wifi = null,
        latitude = null,
        longitude = null,
        geohash = null,
        user = user,
    )
}
