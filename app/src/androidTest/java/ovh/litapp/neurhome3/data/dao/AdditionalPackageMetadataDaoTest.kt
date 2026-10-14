package ovh.litapp.neurhome3.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.models.AdditionalPackageMetadata
import ovh.litapp.neurhome3.data.models.HiddenPackageType

/**
 * Pins the two partial `@Upsert`s of [AdditionalPackageMetadataDao] against a real in-memory Room
 * database: each writes only its own column and must leave the other one alone. The alias and the
 * hide-from flag share one row and are written through different update models, so a regression here
 * would silently drop the alias whenever visibility is toggled (or the reverse).
 */
@RunWith(AndroidJUnit4::class)
class AdditionalPackageMetadataDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: AdditionalPackageMetadataDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.hiddenPackageDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aliasSurvivesAVisibilityUpsert() = runBlocking {
        dao.upsert(UpdateAlias("com.example.mail", user = 0, alias = "Mail alias"))
        dao.upsert(UpdateVisibility("com.example.mail", user = 0, hideFrom = HiddenPackageType.TOP))

        assertEquals(
            AdditionalPackageMetadata(
                packageName = "com.example.mail",
                user = 0,
                hideFrom = HiddenPackageType.TOP,
                alias = "Mail alias",
            ),
            singleRow(),
        )
    }

    @Test
    fun visibilitySurvivesAnAliasUpsert() = runBlocking {
        dao.upsert(UpdateVisibility("com.example.mail", user = 0, hideFrom = HiddenPackageType.FILTERED))
        dao.upsert(UpdateAlias("com.example.mail", user = 0, alias = "Mail alias"))

        assertEquals(HiddenPackageType.FILTERED, singleRow().hideFrom)
        assertEquals("Mail alias", singleRow().alias)
    }

    @Test
    fun clearingTheAliasKeepsTheVisibility() = runBlocking {
        dao.upsert(UpdateVisibility("com.example.mail", user = 0, hideFrom = HiddenPackageType.TOP))
        dao.upsert(UpdateAlias("com.example.mail", user = 0, alias = ""))

        val row = singleRow()
        assertEquals("", row.alias)
        assertEquals(HiddenPackageType.TOP, row.hideFrom)
    }

    @Test
    fun clearingTheVisibilityKeepsTheAlias() = runBlocking {
        dao.upsert(UpdateAlias("com.example.mail", user = 0, alias = "Mail alias"))
        dao.upsert(UpdateVisibility("com.example.mail", user = 0, hideFrom = null))

        val row = singleRow()
        assertNull(row.hideFrom)
        assertEquals("Mail alias", row.alias)
    }

    @Test
    fun upsertsForDistinctProfilesStayIndependent() = runBlocking {
        dao.upsert(UpdateAlias("com.example.mail", user = 0, alias = "Work mail"))
        dao.upsert(UpdateAlias("com.example.mail", user = 10, alias = "Personal mail"))
        dao.upsert(UpdateVisibility("com.example.mail", user = 10, hideFrom = HiddenPackageType.TOP))

        val rows = dao.list().first().associateBy { it.user }
        assertEquals("Work mail", rows[0]!!.alias)
        assertNull(rows[0]!!.hideFrom)
        assertEquals("Personal mail", rows[10]!!.alias)
        assertEquals(HiddenPackageType.TOP, rows[10]!!.hideFrom)
    }

    private suspend fun singleRow(): AdditionalPackageMetadata = dao.list().first().single()
}
