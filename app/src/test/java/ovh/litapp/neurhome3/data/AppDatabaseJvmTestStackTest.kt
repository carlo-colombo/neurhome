package ovh.litapp.neurhome3.data

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ovh.litapp.neurhome3.data.models.Setting
import ovh.litapp.neurhome3.data.models.Tag

/**
 * Proves the JVM Android test stack works end to end: Robolectric provides the Android
 * runtime, the real [AppDatabase] is opened in memory, and kotlinx-coroutines-test drives
 * its suspend DAOs. Later JVM tests can rely on this instead of mocking the database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppDatabaseJvmTestStackTest {
    private lateinit var database: AppDatabase

    @Before
    fun openInMemoryDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun blockingDaoQueriesRunAgainstTheRealInMemoryDatabase() {
        database.settingDao().upsert(Setting(key = "stack", value = "jvm"))

        assertEquals("jvm", database.settingDao().getOnce("stack")?.value)
    }

    @Test
    fun suspendDaoQueriesRunThroughCoroutinesTest() = runTest {
        database.tagDao().insert(Tag(name = "work", position = 0))
        database.tagDao().insert(Tag(name = "home", position = 1))

        assertEquals(
            listOf("work", "home"),
            database.tagDao().list().first().map { it.name },
        )
    }
}
