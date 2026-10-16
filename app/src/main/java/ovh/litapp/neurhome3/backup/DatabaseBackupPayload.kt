package ovh.litapp.neurhome3.backup

import android.app.backup.BackupAgent
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * File name of the compressed database payload that [NeurhomeBackupAgent] appends to a full
 * backup on top of the framework's own pass.
 *
 * The payload deliberately lives next to `neurhome_database` in the `databases` directory
 * instead of the cache directory. `BackupAgent.fullBackupFile` silently ignores files under
 * the cache, code-cache, no-backup, and native-library directories, and only files inside a
 * recognised backup domain get a backup path the restore side can route back. A sibling of the
 * database is recorded in the `database` domain, so restore delivers it to
 * `onRestoreFile` as `<databases>/neurhome_database.backup.gz`.
 */
const val DATABASE_PAYLOAD_FILE_NAME = "neurhome_database.backup.gz"

/**
 * Scratch file that [NeurhomeBackupAgent] decompresses a restore payload into before moving it
 * over the live database. Lives in the `databases` directory so the move is same-filesystem
 * and can replace the database atomically.
 */
const val DATABASE_RESTORE_SCRATCH_FILE_NAME = "neurhome_database.restoring"

/** SQLite sidecar files that must be dropped when the main database file is replaced. */
private val DATABASE_SIDECAR_SUFFIXES = listOf("-wal", "-shm", "-journal")

/**
 * Whether [NeurhomeBackupAgent.onFullBackup] may append the compressed database payload to the
 * transport described by [transportFlags].
 *
 * The database is intentionally absent from the `<cloud-backup>` rule section, so this decision
 * governs the cloud path entirely. Client-side encryption (CSE) is enforced here rather than by
 * the rule XML: declaring a custom agent and overriding `onFullBackup` means the framework's
 * `requireFlags="clientSideEncryption"` attribute no longer governs the payload this method
 * writes, so the requirement is re-imposed from the transport flags. A cloud transport without
 * CSE therefore gets no database payload, matching the rest of the backup, which the framework
 * also drops without CSE.
 *
 * Device-to-device transfer is not a cloud path and does not advertise CSE. It keeps the
 * database in its own `<device-transfer>` rule section, where the framework uploads it raw, so
 * the agent must not add a second copy there.
 */
fun shouldUploadDatabasePayload(transportFlags: Int): Boolean {
    val clientSideEncryptionEnabled =
        transportFlags and BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED != 0
    val deviceToDeviceTransfer =
        transportFlags and BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER != 0
    return clientSideEncryptionEnabled && !deviceToDeviceTransfer
}

/**
 * The path [NeurhomeBackupAgent] compresses [databaseFile] into. It is a sibling of the database
 * so the framework records it in the `database` domain (see [DATABASE_PAYLOAD_FILE_NAME]).
 */
fun databasePayloadFile(databaseFile: File): File =
    File(databaseFile.parentFile, DATABASE_PAYLOAD_FILE_NAME)

/** True when [file] is the compressed payload rather than the live database. */
fun isDatabasePayloadFile(file: File): Boolean = file.name == DATABASE_PAYLOAD_FILE_NAME

/** Gzip-compresses [source] into [destination], replacing any existing content. */
fun gzipTo(source: File, destination: File) {
    FileInputStream(source).use { input ->
        GZIPOutputStream(FileOutputStream(destination)).use { output ->
            input.copyTo(output)
        }
    }
}

/** Decompresses the gzip payload at [source] into [destination], replacing any existing content. */
fun gunzipTo(source: File, destination: File) {
    GZIPInputStream(FileInputStream(source)).use { input ->
        FileOutputStream(destination).use { output ->
            input.copyTo(output)
        }
    }
}

/** Deletes the `-wal`, `-shm`, and legacy `-journal` sidecars of [databaseFile]. */
fun deleteDatabaseSidecars(databaseFile: File) {
    for (suffix in DATABASE_SIDECAR_SUFFIXES) {
        File(databaseFile.path + suffix).takeIf { it.exists() }?.delete()
    }
}
