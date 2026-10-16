package ovh.litapp.neurhome3.data.dao

import android.app.Application
import androidx.room.Room
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.models.Setting

/**
 * Runs [SettingDao.toggle] against a real in-memory Room database. Every user-visible toggle
 * ("show alternative time", the permission opt-ins) goes through this one method, and its contract
 * is that the row is read and written as a unit instead of inferring the current state from a
 * primary-key conflict.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: SettingDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.settingDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun toggleInsertsAnAbsentKeyAndRemovesTheKeyWhenItIsPresent() {
        dao.toggle("show.alternative.time", "true")
        assertEquals("true", dao.getOnce("show.alternative.time")?.value)

        dao.toggle("show.alternative.time", "true")

        assertNull(dao.getOnce("show.alternative.time"))
        assertEquals(emptyList<Setting>(), dao.getAll())
    }

    @Test
    fun toggleRemovesAnExistingKeyWhateverValueItCurrentlyHolds() {
        // The read decides, not the value: a key stored as "false" is still present, so toggling
        // it removes the row instead of overwriting it with the passed value.
        dao.upsert(Setting(key = "show.calendar", value = "false"))

        dao.toggle("show.calendar", "true")

        assertNull(dao.getOnce("show.calendar"))
    }

    @Test
    fun toggleLeavesOtherKeysUntouched() {
        dao.upsert(Setting(key = "log.wifi", value = "true"))

        dao.toggle("show.calendar", "true")

        assertEquals("true", dao.getOnce("log.wifi")?.value)
        assertEquals("true", dao.getOnce("show.calendar")?.value)
    }

    @Test
    fun concurrentTogglesOfOneKeyAreSerialisedByTheTransaction() {
        val key = "flip.me"
        val executor = Executors.newFixedThreadPool(2)
        try {
            repeat(20) { round ->
                val barrier = CyclicBarrier(2)
                val flips = (1..2).map {
                    executor.submit {
                        barrier.await(30, TimeUnit.SECONDS)
                        dao.toggle(key, "true")
                    }
                }
                flips.forEach { it.get(30, TimeUnit.SECONDS) }

                // Two flips cancel out only if the second one read what the first one wrote. The
                // deleted-and-reinserted row is keyed by `key`, so `round` also proves the row
                // left no residue from the previous flip.
                assertNull("round $round", dao.getOnce(key))
            }

            dao.toggle(key, "true")

            assertEquals("true", dao.getOnce(key)?.value)
        } finally {
            executor.shutdownNow()
        }
    }
}
