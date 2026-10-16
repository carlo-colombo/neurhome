package ovh.litapp.neurhome3.application

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ovh.litapp.neurhome3.data.NEURHOME_DATABASE_VERSION
import java.io.File

/**
 * Robolectric-backed tests for the pre-swap gate: a real SQLite file is built on disk and
 * handed to [DatabaseCandidateValidator], so the reject paths are exercised against the
 * same `SQLiteDatabase` API the app uses. The tests pin that a usable candidate is
 * accepted and that a non-database file and an out-of-range schema version are rejected.
 * The surrounding swap (quiesce/close/atomic move/rebind) needs the full application graph
 * and is not covered here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DatabaseCandidateValidatorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun acceptsReadableDatabaseWithinTheSupportedVersionRange() {
        val candidate = createDatabase(version = NEURHOME_DATABASE_VERSION)

        assertEquals(
            DatabaseCandidateValidation.Valid,
            DatabaseCandidateValidator.validate(candidate, NEURHOME_DATABASE_VERSION),
        )
    }

    @Test
    fun acceptsTheOldestMigratableSchemaVersion() {
        val candidate = createDatabase(version = DatabaseCandidateValidator.MIN_SCHEMA_VERSION)

        assertEquals(
            DatabaseCandidateValidation.Valid,
            DatabaseCandidateValidator.validate(candidate, NEURHOME_DATABASE_VERSION),
        )
    }

    @Test
    fun rejectsAFileThatIsNotASQLiteDatabase() {
        val candidate = temporaryFolder.newFile("not-a-database.db")
        candidate.writeBytes(ByteArray(4096) { (it * 31 % 251).toByte() })

        assertEquals(
            DatabaseCandidateValidation.NotADatabase,
            DatabaseCandidateValidator.validate(candidate, NEURHOME_DATABASE_VERSION),
        )
    }

    @Test
    fun rejectsAnEmptyFile() {
        val candidate = temporaryFolder.newFile("empty.db")

        assertEquals(
            DatabaseCandidateValidation.NotADatabase,
            DatabaseCandidateValidator.validate(candidate, NEURHOME_DATABASE_VERSION),
        )
    }

    @Test
    fun rejectsASchemaVersionNewerThanThisBuildCanMigrate() {
        val newer = NEURHOME_DATABASE_VERSION + 1
        val candidate = createDatabase(version = newer)

        assertEquals(
            DatabaseCandidateValidation.UnsupportedVersion(newer),
            DatabaseCandidateValidator.validate(candidate, NEURHOME_DATABASE_VERSION),
        )
    }

    @Test
    fun rejectsASchemaVersionOlderThanTheMigratableRange() {
        val candidate = createDatabase(version = DatabaseCandidateValidator.MIN_SCHEMA_VERSION - 1)

        assertEquals(
            DatabaseCandidateValidation.UnsupportedVersion(0),
            DatabaseCandidateValidator.validate(candidate, NEURHOME_DATABASE_VERSION),
        )
    }

    private fun createDatabase(version: Int): File {
        val file = temporaryFolder.newFile("candidate-$version.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE probe (id INTEGER PRIMARY KEY)")
            database.version = version
        }
        return file
    }
}
