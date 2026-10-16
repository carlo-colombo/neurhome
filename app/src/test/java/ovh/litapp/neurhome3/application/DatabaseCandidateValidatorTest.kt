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
 * accepted and that a non-database file, an empty file, a database whose pages are damaged and
 * an out-of-range schema version are rejected. The surrounding swap (quiesce/close/atomic
 * move/rebind) needs the full application graph and is not covered here.
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
    fun rejectsADatabaseWhosePagesAreDamagedEvenThoughItStillOpens() {
        val candidate = createMultiPageDatabase(version = NEURHOME_DATABASE_VERSION)
        scribbleSecondPage(candidate)

        // The header and the schema version survive, so opening the file and reading `user_version`
        // both succeed; only `PRAGMA quick_check` notices the damaged page.
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

    /** A database with enough rows to spread the table over several 4 KiB pages. */
    private fun createMultiPageDatabase(version: Int): File {
        val file = temporaryFolder.newFile("candidate-multi-page-$version.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE probe (id INTEGER PRIMARY KEY, payload TEXT NOT NULL)")
            for (row in 1..200) {
                database.execSQL("INSERT INTO probe (payload) VALUES ('payload number $row')")
            }
            database.version = version
        }
        return file
    }

    /** Overwrites the file's second 4 KiB page, leaving page one and the `user_version` intact. */
    private fun scribbleSecondPage(candidate: File) {
        val bytes = candidate.readBytes()
        check(bytes.size > 2 * PAGE_SIZE) { "expected a multi-page database, got ${bytes.size} bytes" }
        for (offset in PAGE_SIZE until 2 * PAGE_SIZE) {
            bytes[offset] = (offset * 7 % 251).toByte()
        }
        candidate.writeBytes(bytes)
    }

    private companion object {
        /** The default page size of the SQLite build Robolectric runs. */
        const val PAGE_SIZE = 4096
    }
}
