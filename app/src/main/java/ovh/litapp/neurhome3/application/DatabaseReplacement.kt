package ovh.litapp.neurhome3.application

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import java.io.File

/**
 * Outcome of [NeurhomeApplication.replaceDatabase]. The caller (the Settings flow in
 * `MainActivity`) only relaunches the UI for [Replaced]; every other value leaves the
 * process running over the database that was already in place.
 */
sealed interface DatabaseReplacementResult {
    /** The picked file passed validation and now backs the database; relaunch the UI. */
    data object Replaced : DatabaseReplacementResult

    /** The picked file is not a readable SQLite database. */
    data object NotADatabase : DatabaseReplacementResult

    /** A real database, but a schema version this build has no migration path for. */
    data class UnsupportedVersion(val schemaVersion: Int) : DatabaseReplacementResult

    /** The file could not be read, staged, or moved into place; nothing was swapped. */
    data object IoError : DatabaseReplacementResult
}

/** Result of checking a staged candidate before it is allowed to replace the live database. */
internal sealed interface DatabaseCandidateValidation {
    data object Valid : DatabaseCandidateValidation
    data object NotADatabase : DatabaseCandidateValidation
    data class UnsupportedVersion(val schemaVersion: Int) : DatabaseCandidateValidation
}

/**
 * Pre-swap gate for a picked database file. The file is opened read-only, so a bad
 * candidate cannot mutate anything, and [SQLiteDatabase.getVersion] exposes Room's
 * `user_version`.
 *
 * The checks are deliberately cheap: opening the file rejects anything that is not a
 * SQLite database (a random octet-stream fails here), `user_version` must be inside the
 * range this build can migrate, and `PRAGMA quick_check(1)` catches a truncated or
 * scribbled page. `quick_check` is the fast variant of `integrity_check` and is enough
 * to keep a broken file from becoming the live database. It is not a full backup
 * validation; deep corruption beyond the first bad page still surfaces as a Room error
 * after the swap.
 */
internal object DatabaseCandidateValidator {
    /** Oldest schema version this build ships a migration path from. */
    const val MIN_SCHEMA_VERSION = 1

    fun validate(candidate: File, maxSchemaVersion: Int): DatabaseCandidateValidation {
        if (!candidate.isFile || candidate.length() == 0L) {
            return DatabaseCandidateValidation.NotADatabase
        }
        val database = try {
            SQLiteDatabase.openDatabase(candidate.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: SQLiteException) {
            return DatabaseCandidateValidation.NotADatabase
        }
        return database.use { db ->
            val schemaVersion = try {
                db.version
            } catch (e: SQLiteException) {
                return DatabaseCandidateValidation.NotADatabase
            }
            if (schemaVersion < MIN_SCHEMA_VERSION || schemaVersion > maxSchemaVersion) {
                return DatabaseCandidateValidation.UnsupportedVersion(schemaVersion)
            }
            if (!quickCheckPasses(db)) {
                return DatabaseCandidateValidation.NotADatabase
            }
            DatabaseCandidateValidation.Valid
        }
    }

    private fun quickCheckPasses(database: SQLiteDatabase): Boolean = try {
        database.rawQuery("PRAGMA quick_check(1)", null).use { cursor ->
            cursor.moveToFirst() && cursor.getString(0) == "ok"
        }
    } catch (e: SQLiteException) {
        false
    }
}
