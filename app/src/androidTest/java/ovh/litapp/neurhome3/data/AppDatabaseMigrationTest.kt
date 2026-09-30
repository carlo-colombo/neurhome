package ovh.litapp.neurhome3.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @Test
    fun v19RowsMigrateToUnknownWithoutChangingTheirExistingValues() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "migration-v19-${System.nanoTime()}.db"
        val databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.mkdirs()

        createV19Fixture(databaseFile)

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        try {
            val rows = migrated.openHelper.readableDatabase.query(
                "SELECT uid, wifiState, packageName, timestamp, wifi, latitude, longitude, " +
                    "geohash, user, query " +
                    "FROM ApplicationLogEntry ORDER BY uid"
            )
            rows.use {
                assertEquals(2, it.count)
                it.moveToFirst()

                assertEquals(1, it.getInt(0))
                assertEquals("UNKNOWN", it.getString(1))
                assertEquals("com.example.mail", it.getString(2))
                assertEquals("2026-09-30T12:00:00", it.getString(3))
                assertEquals("Home Wi-Fi", it.getString(4))
                assertEquals(37.4, it.getDouble(5), 0.00001)
                assertEquals(-122.1, it.getDouble(6), 0.00001)
                assertEquals("9q9hvu123", it.getString(7))
                assertEquals(0, it.getInt(8))
                assertEquals("mail", it.getString(9))

                it.moveToNext()
                assertEquals(2, it.getInt(0))
                assertEquals("UNKNOWN", it.getString(1))
                assertEquals("com.example.camera", it.getString(2))
                assertEquals("2026-09-30T12:00:00", it.getString(3))
                assertNull(it.getString(4))
                assertEquals(true, it.isNull(5))
                assertEquals(true, it.isNull(6))
                assertNull(it.getString(7))
                assertEquals(0, it.getInt(8))
                assertNull(it.getString(9))
            }
            assertEquals(20, migrated.openHelper.readableDatabase.version)
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun createV19Fixture(file: File) {
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            database.execSQL("CREATE TABLE `Setting` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
            database.execSQL("""CREATE TABLE `ApplicationLogEntry` (
                `uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `packageName` TEXT NOT NULL,
                `timestamp` TEXT NOT NULL,
                `wifi` TEXT,
                `latitude` REAL,
                `longitude` REAL,
                `geohash` TEXT,
                `user` INTEGER NOT NULL DEFAULT 0,
                `query` TEXT
            )""".trimIndent())
            database.execSQL("CREATE INDEX `index_ApplicationLogEntry_packageName` ON `ApplicationLogEntry` (`packageName` DESC)")
            database.execSQL("CREATE INDEX `index_ApplicationLogEntry_timestamp` ON `ApplicationLogEntry` (`timestamp`)")
            database.execSQL("CREATE INDEX `index_ApplicationLogEntry_user_packageName_timestamp` ON `ApplicationLogEntry` (`user`, `packageName`, `timestamp`)")
            database.execSQL("""CREATE TABLE `AdditionalPackageMetadata` (
                `packageName` TEXT NOT NULL,
                `user` INTEGER NOT NULL DEFAULT 0,
                `hideFrom` TEXT,
                `alias` TEXT,
                PRIMARY KEY(`packageName`, `user`)
            )""".trimIndent())
            database.execSQL("CREATE INDEX `index_AdditionalPackageMetadata_hideFrom` ON `AdditionalPackageMetadata` (`hideFrom`)")
            database.execSQL("CREATE TABLE `Tag` (`name` TEXT NOT NULL, `position` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`name`))")
            database.execSQL("CREATE TABLE `ApplicationTag` (`packageName` TEXT NOT NULL, `profile` INTEGER NOT NULL, `tagName` TEXT NOT NULL, PRIMARY KEY(`packageName`, `profile`, `tagName`))")
            database.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            database.execSQL(
                "INSERT INTO room_master_table (id, identity_hash) VALUES (42, '8c7a788b10bbf70b28b263ca3919f2bc')"
            )

            insertLegacyRow(
                database,
                packageName = "com.example.mail",
                wifi = "Home Wi-Fi",
                latitude = 37.4,
                longitude = -122.1,
                geohash = "9q9hvu123",
                query = "mail"
            )
            insertLegacyRow(database, packageName = "com.example.camera")
            database.version = 19
        } finally {
            database.close()
        }
    }

    private fun insertLegacyRow(
        database: SQLiteDatabase,
        packageName: String,
        wifi: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        geohash: String? = null,
        query: String? = null
    ) {
        val values = ContentValues().apply {
            put("packageName", packageName)
            put("timestamp", "2026-09-30T12:00:00")
            wifi?.let { put("wifi", it) }
            latitude?.let { put("latitude", it) }
            longitude?.let { put("longitude", it) }
            geohash?.let { put("geohash", it) }
            query?.let { put("query", it) }
        }
        database.insertOrThrow("ApplicationLogEntry", null, values)
    }
}
