package ovh.litapp.neurhome3.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.database.sqlite.SQLiteDatabase
import android.os.ParcelFileDescriptor
import android.util.Log
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.NEURHOME_DATABASE
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream

private const val TAG = "NeurhomeBackupAgent"

/**
 * Owns the Room database during auto backup and device transfer.
 *
 * The database grew past the 25 MB Auto Backup cloud quota, so the framework would silently
 * skip it. This agent keeps the framework's default handling for every other domain (the
 * unchanged `data_extraction_rules.xml` / `backup_rules.xml` scheme) and adds the database as a
 * gzip-compressed payload instead, which brings it well under the quota.
 *
 * ## Client-side encryption is enforced in code, not by the XML
 *
 * Declaring a custom agent takes over the backup path. [BackupAgent.onFullBackup] consults the
 * `dataExtractionRules` / `fullBackupContent` scheme, but because this class overrides it and
 * adds a file with [BackupAgent.fullBackupFile], the `requireFlags="clientSideEncryption"`
 * attribute in the rule XML no longer governs that file. The CSE requirement is therefore
 * re-imposed directly from [FullBackupDataOutput.getTransportFlags] in
 * [shouldUploadDatabasePayload]; a cloud transport without CSE receives no database payload.
 *
 * ## The database domain is omitted from the cloud-backup rule section
 *
 * `<cloud-backup>` no longer lists the `database` domain as an `<include>` (a section with any
 * include is narrowed to exactly the listed paths, so omission already drops the raw file), and
 * no `<exclude>` is used because Android Lint's `FullBackupContent` check rejects an exclude whose
 * domain has no matching include. `super.onFullBackup` therefore does not upload the raw database
 * and this agent's compressed payload is the only cloud copy. `<device-transfer>` still includes
 * the database, so a device-to-device transfer carries the raw file via `super.onFullBackup` and
 * this agent adds nothing for it. That is what keeps the database from being uploaded twice.
 */
class NeurhomeBackupAgent : BackupAgent() {

    /** Set when a restore actually replaced the database, so [onRestoreFinished] can act. */
    @Volatile
    private var databasePayloadRestored = false

    override fun onFullBackup(data: FullBackupDataOutput) {
        val databaseFile = getDatabasePath(NEURHOME_DATABASE)
        val payloadFile = databasePayloadFile(databaseFile)

        // A crash during an earlier backup can leave the payload behind. The databases directory
        // is included in the device-transfer rules, so delete it before the framework's own pass
        // to be sure it is never uploaded twice.
        if (payloadFile.exists() && !payloadFile.delete()) {
            Log.w(TAG, "Could not delete stale database payload at ${payloadFile.path}")
        }

        // Everything except the database keeps the current behaviour and the rule XML.
        super.onFullBackup(data)

        if (!shouldUploadDatabasePayload(data.transportFlags)) {
            Log.i(
                TAG,
                "Not adding the compressed database: transport flags ${data.transportFlags} " +
                    "do not allow it (CSE required for cloud, device transfer already has it)."
            )
            return
        }
        if (!databaseFile.isFile) {
            Log.i(TAG, "No database at ${databaseFile.path}; nothing to add.")
            return
        }

        try {
            checkpointDatabase(databaseFile)
            gzipTo(databaseFile, payloadFile)
            fullBackupFile(payloadFile, data)
            Log.i(TAG, "Added compressed database payload (${payloadFile.length()} bytes).")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to add the compressed database payload.", e)
        } finally {
            if (payloadFile.exists() && !payloadFile.delete()) {
                Log.w(TAG, "Could not delete database payload at ${payloadFile.path}")
            }
        }
    }

    /**
     * Room opens the database in WAL mode, so copying the main file alone can miss the newest
     * transactions still living in `-wal`. `wal_checkpoint(TRUNCATE)` folds every committed page
     * back into the main file and truncates the log, making the copy complete.
     */
    private fun checkpointDatabase(databaseFile: File) {
        try {
            SQLiteDatabase.openDatabase(
                databaseFile.path,
                null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            ).use { database ->
                database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                    if (cursor.moveToFirst() && cursor.getInt(0) != 0) {
                        // SQLite returns (busy, log, checkpointed) pages; a busy checkpoint can
                        // leave recent writes in the log, so the copy would be incomplete.
                        Log.w(TAG, "wal_checkpoint reported busy; the database copy may miss recent writes.")
                    }
                }
            }
        } catch (e: RuntimeException) {
            // A locked or unreadable database must not abort the rest of the backup.
            Log.w(TAG, "Could not checkpoint the database before copying it.", e)
        }
    }

    /**
     * Restores the compressed payload directly into the live database path. The framework's own
     * restore would reject it (the payload is not in the `<cloud-backup>` include list) and would
     * write the gzip bytes to a file named `*.backup.gz`, so this branch replaces that handling.
     */
    override fun onRestoreFile(
        data: ParcelFileDescriptor,
        size: Long,
        destination: File,
        type: Int,
        mode: Long,
        mtime: Long
    ) {
        if (type == BackupAgent.TYPE_FILE && isDatabasePayloadFile(destination)) {
            restoreDatabasePayload(data)
            return
        }
        super.onRestoreFile(data, size, destination, type, mode, mtime)
    }

    private fun restoreDatabasePayload(data: ParcelFileDescriptor) {
        val databaseFile = getDatabasePath(NEURHOME_DATABASE)
        val scratchFile = File(databaseFile.parentFile, DATABASE_RESTORE_SCRATCH_FILE_NAME)
        try {
            databaseFile.parentFile?.mkdirs()

            // The framework's own onRestoreFile reads the payload with a FileInputStream over the
            // descriptor and leaves closing it to the restore engine; closing it here would
            // double-close the same fd. The no-op close lets GZIPInputStream release its inflater
            // without touching the descriptor.
            val raw = FileInputStream(data.fileDescriptor)
            val noCloseRaw = object : FilterInputStream(raw) {
                override fun close() = Unit
            }
            GZIPInputStream(noCloseRaw).use { input ->
                FileOutputStream(scratchFile).use { output -> input.copyTo(output) }
            }

            // Move the decompressed file over the database in one step, then drop sidecars: a
            // leftover -wal/-shm would otherwise be replayed over the restored main file on the
            // next open and corrupt or roll back the restore.
            Files.move(
                scratchFile.toPath(),
                databaseFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            deleteDatabaseSidecars(databaseFile)
            databasePayloadRestored = true
            Log.i(TAG, "Restored the database payload to ${databaseFile.path}.")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to restore the database payload.", e)
        } finally {
            if (scratchFile.exists() && !scratchFile.delete()) {
                Log.w(TAG, "Could not delete restore scratch file at ${scratchFile.path}")
            }
        }
    }

    override fun onRestoreFinished() {
        if (databasePayloadRestored) {
            // The agent can run inside a process that already opened the database. Room keeps one
            // instance per process and never reopens a closed one, so drop the cached singleton;
            // the next getDatabase() call reads the restored file. The framework restarts the app
            // process after a full restore, so any handle the running process still held is
            // disposed of with it.
            AppDatabase.forgetInstance()
        }
        super.onRestoreFinished()
    }

    /**
     * Auto Backup and device transfer both run through [onFullBackup]/[onRestoreFile]. The
     * key-value entry points are unused, but [BackupAgent] declares them abstract, so they are
     * no-ops.
     */
    override fun onBackup(
        oldState: ParcelFileDescriptor,
        data: BackupDataOutput,
        newState: ParcelFileDescriptor
    ) = Unit

    /** Key-value restore is unused; see [onBackup]. */
    override fun onRestore(
        data: BackupDataInput,
        appVersionCode: Int,
        newState: ParcelFileDescriptor
    ) = Unit

    /**
     * Called when the transport rejects the backup for exceeding its quota. The framework halts
     * and rolls back the operation; this hook cannot raise the quota, only make the condition
     * visible instead of silent.
     */
    override fun onQuotaExceeded(backupDataBytes: Long, quotaBytes: Long) {
        Log.w(
            TAG,
            "Backup exceeded the quota (${backupDataBytes} bytes submitted, " +
                "${quotaBytes} bytes allowed); the backup was rolled back."
        )
    }
}
