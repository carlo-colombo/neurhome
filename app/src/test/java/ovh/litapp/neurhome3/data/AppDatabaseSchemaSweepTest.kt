package ovh.litapp.neurhome3.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Migrates a database built from every committed schema below the current version - 1 through 22, one
 * parameterized test each - up to [NEURHOME_DATABASE_VERSION] with the real [AppDatabase].
 *
 * Every hop of the declared `AutoMigration` chain is therefore exercised: Room has to find a path from
 * the fixture's version to the current one, and it validates the migrated schema against the generated
 * one afterwards, so a missing hop or a hop that produces the wrong tables fails here. The parameters
 * are the *expected* versions rather than the files that happen to be committed, so a schema JSON that
 * was deleted (or was never exported when its version became current) fails its own test instead of
 * silently shrinking the sweep. `AppDatabaseMigrationTest` covers the data-preservation of the
 * 19 -> 23 span in detail; this sweep covers the earlier span, which had no test at all.
 *
 * The fixtures are built from the committed schema JSONs, one `CREATE TABLE` per entity plus its
 * indices plus the file's `setupQueries` with `${TABLE_NAME}` substituted - the same queries
 * `MigrationTestHelper` runs when it builds its fixture. The helper itself is not used because its
 * constructors load the schema files from the instrumentation's assets, and a JVM unit test only ever
 * sees the app variant's merged assets (AGP has no unit-test asset merge), so reaching it from here
 * would mean shipping `app/schemas` inside the APK. Reading the committed JSONs from the source tree
 * keeps the sweep in the fast, device-free test task that the gate runs.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = Application::class)
class AppDatabaseSchemaSweepTest(private val schemaVersion: Int) {
    @Test
    fun theCommittedSchemaMigratesToTheCurrentVersion() {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "sweep-v$schemaVersion-${System.nanoTime()}.db"
        val databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.mkdirs()

        val fixture = schema(schemaVersion)
        assertEquals(schemaVersion, fixture.version)
        createFixture(fixture, databaseFile)

        val database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        try {
            val opened = database.openHelper.readableDatabase

            assertEquals(
                "the fixture must end up on the version the app declares",
                NEURHOME_DATABASE_VERSION,
                opened.version,
            )
            assertEquals(
                "the migrated database must carry exactly the current schema's tables",
                schema(NEURHOME_DATABASE_VERSION).tableNames(),
                opened.tableNames(),
            )
            assertEquals(
                "a row written before every migration must survive them all",
                "v$schemaVersion",
                opened.settingValue(SWEEP_KEY),
            )
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    /** Builds the schema database file the way `MigrationTestHelper` builds its fixture. */
    private fun createFixture(schema: SchemaJson, file: File) {
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            val createQueries = schema.entities.flatMap { entity ->
                listOf(entity.createSql.replaceTableName(entity.tableName)) +
                    entity.indices.map { it.createSql.replaceTableName(entity.tableName) }
            } + schema.setupQueries
            createQueries.forEach { database.execSQL(it) }
            database.execSQL(
                "INSERT INTO `Setting` (`key`, `value`) VALUES ('$SWEEP_KEY', 'v${schema.version}')"
            )
            database.version = schema.version
        } finally {
            database.close()
        }
    }

    private fun SupportSQLiteDatabase.tableNames(): Set<String> =
        query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    // Room's bookkeeping, plus SQLite's own tables: android_metadata is created by
                    // the framework's SQLiteDatabase and sqlite_* are internal.
                    if (name != "room_master_table" && name != "android_metadata" &&
                        !name.startsWith("sqlite_")
                    ) {
                        add(name)
                    }
                }
            }
        }

    private fun SupportSQLiteDatabase.settingValue(key: String): String? =
        query("SELECT `value` FROM `Setting` WHERE `key` = ?", arrayOf(key)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun String.replaceTableName(tableName: String): String =
        replace(TABLE_NAME_PLACEHOLDER, tableName)

    @Serializable
    private data class SchemaJson(
        val database: DatabaseJson,
    ) {
        val version: Int get() = database.version
        val entities: List<EntityJson> get() = database.entities
        val setupQueries: List<String> get() = database.setupQueries

        fun tableNames(): Set<String> = entities.map { it.tableName }.toSet()
    }

    @Serializable
    private data class DatabaseJson(
        val version: Int,
        val entities: List<EntityJson> = emptyList(),
        val setupQueries: List<String> = emptyList(),
    )

    @Serializable
    private data class EntityJson(
        val tableName: String,
        val createSql: String,
        val indices: List<IndexJson> = emptyList(),
    )

    @Serializable
    private data class IndexJson(val createSql: String)

    private companion object {
        const val SWEEP_KEY = "schema.sweep"
        const val TABLE_NAME_PLACEHOLDER = "\${TABLE_NAME}"
        const val SCHEMA_FOLDER = "ovh.litapp.neurhome3.data.AppDatabase"

        /** Unknown keys (fields, primary keys, format version) are not needed to build the fixture. */
        val json = Json { ignoreUnknownKeys = true }

        /** The exported schemas live next to the module, which is this test's working directory. */
        fun schema(version: Int): SchemaJson {
            val file = File(schemaDirectory(), "$version.json")
            check(file.isFile) {
                "no committed schema for version $version at $file; commit the exported $version.json"
            }
            return file.inputStream().use { json.decodeFromString(SchemaJson.serializer(), it.readBytes().decodeToString()) }
        }

        fun schemaDirectory(): File {
            val candidates = generateSequence(File(System.getProperty("user.dir")).absoluteFile) {
                it.parentFile
            }.flatMap { directory ->
                sequenceOf(
                    File(directory, "app/schemas/$SCHEMA_FOLDER"),
                    File(directory, "schemas/$SCHEMA_FOLDER"),
                )
            }
            return candidates.firstOrNull { it.isDirectory }
                ?: error("cannot find the committed Room schemas under ${System.getProperty("user.dir")}")
        }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "schema {0}")
        fun schemaVersions(): List<Array<Any>> =
            (1 until NEURHOME_DATABASE_VERSION).map { arrayOf<Any>(it) }
    }
}
