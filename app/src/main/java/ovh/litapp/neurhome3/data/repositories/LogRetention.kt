package ovh.litapp.neurhome3.data.repositories

import android.util.Log
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ovh.litapp.neurhome3.data.dao.ApplicationLogEntryDao
import ovh.litapp.neurhome3.data.dao.SettingDao
import ovh.litapp.neurhome3.data.models.Setting
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val TAG = "LogRetention"

/** Amount of launch history kept; anything older is pruned. */
const val LOG_RETENTION_YEARS = 2L

/**
 * Minimum freelist share of the database pages before a retention pass pays for a `VACUUM`.
 * Below this the whole-file rewrite costs more I/O than the space it returns.
 */
const val VACUUM_FREELIST_PERCENT = 25L

/**
 * Cutoff below which launch rows are pruned: [now] shifted back [LOG_RETENTION_YEARS].
 *
 * Stored timestamps are `DateTimeFormatter.ISO_LOCAL_DATE_TIME` strings, so the formatted cutoff
 * orders chronologically under a plain lexicographic SQL `<` without parsing dates in SQL.
 */
fun retentionCutoff(now: LocalDateTime): String =
    now.minusYears(LOG_RETENTION_YEARS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

/** True when the freelist is large enough (see [VACUUM_FREELIST_PERCENT]) to justify a `VACUUM`. */
fun isVacuumWorthwhile(freelistCount: Long, pageCount: Long): Boolean =
    pageCount > 0 && freelistCount * 100 >= pageCount * VACUUM_FREELIST_PERCENT

/**
 * Enforces the launch-log retention policy: once a day (tracked in the `Setting` table so it
 * survives process restarts) it deletes rows older than [LOG_RETENTION_YEARS] and reclaims the
 * freed file space with a `VACUUM` when that is worthwhile. It runs on [Dispatchers.IO] only, has
 * no UI dependency, and is safe to fire-and-forget from application startup.
 */
class LogRetention(
    private val logEntryDao: ApplicationLogEntryDao,
    private val settingDao: SettingDao,
    private val database: RoomDatabase,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    /** Fire-and-forget startup entry point. Failures are logged, never propagated. */
    fun pruneInBackground(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                pruneIfDue()
            } catch (e: Exception) {
                Log.w(TAG, "Log retention pass failed", e)
            }
        }
    }

    /**
     * Runs one retention pass unless it already ran today. A failed pass leaves the last-run
     * marker untouched, so the next launch retries it.
     */
    suspend fun pruneIfDue() {
        val now = now()
        val today = now.toLocalDate().toString()
        if (settingDao.getOnce(LOG_RETENTION_LAST_RUN_SETTING)?.value == today) return

        val deleted = logEntryDao.deleteOlderThan(retentionCutoff(now))
        reclaimSpace()
        settingDao.upsert(Setting(LOG_RETENTION_LAST_RUN_SETTING, today))
        Log.d(TAG, "Log retention pruned $deleted row(s)")
    }

    /**
     * `VACUUM` cannot run inside a transaction, so it runs standalone against the support database
     * rather than through `withTransaction`. It rewrites the whole file, so it is skipped unless the
     * freelist has grown enough to matter.
     */
    private fun reclaimSpace() {
        val support = database.openHelper.writableDatabase
        val freelistCount = support.query("PRAGMA freelist_count").use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
        val pageCount = support.query("PRAGMA page_count").use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
        if (!isVacuumWorthwhile(freelistCount, pageCount)) return
        support.execSQL("VACUUM")
    }
}
