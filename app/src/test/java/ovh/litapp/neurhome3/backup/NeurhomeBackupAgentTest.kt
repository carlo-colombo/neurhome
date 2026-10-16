package ovh.litapp.neurhome3.backup

import android.app.Application
import android.app.backup.BackupAgent
import android.app.backup.FullBackupDataOutput
import android.database.sqlite.SQLiteDatabase
import android.os.ParcelFileDescriptor
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.BackupAgentController
import org.robolectric.annotation.Config
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.NEURHOME_DATABASE

/**
 * Drives [NeurhomeBackupAgent] itself, which had no coverage at all: what the payload branch of
 * `onFullBackup` does to the live database before the framework's transport call, what
 * `onRestoreFile` replaces once it recognises the payload by file name, and what
 * `onRestoreFinished` does to the cached [AppDatabase] instance.
 *
 * ## What this genuinely cannot cover
 *
 * The backup transport is not fakeable on the JVM, so the tests never assert bytes leaving the
 * process:
 *
 *  - Robolectric stubs `FullBackup.backupToTar` to a no-op, so the payload handed to
 *    `BackupAgent.fullBackupFile` cannot be observed (the destination file stays empty). The
 *    checkpoint that makes that payload complete, the transport-flag gate in front of it and the
 *    payload's removal afterwards are observable and are what these tests assert.
 *  - `type` is half of the restore-side guard `type == TYPE_FILE && isDatabasePayloadFile(...)`.
 *    The name check is covered; the type check is not, because a tar entry named
 *    `neurhome_database.backup.gz` is always a regular file in production.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NeurhomeBackupAgentTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var context: Application
    private lateinit var agent: NeurhomeBackupAgent
    private lateinit var databaseFile: File

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Robolectric leaves ApplicationInfo.nativeLibraryDir null, which makes the framework's
        // BackupAgent.fullBackupFile throw while classifying the payload path and would stop
        // onFullBackup before the payload branch. A real path lets the production code run.
        context.applicationInfo.nativeLibraryDir = "/data/app/ovh.litapp.neurhome3/lib"
        agent = BackupAgentController.of(NeurhomeBackupAgent()).create().get()
        databaseFile = context.getDatabasePath(NEURHOME_DATABASE)
    }

    @After
    fun tearDown() {
        AppDatabase.forgetInstance()
    }

    @Test
    fun cloudBackupWithClientSideEncryptionCheckpointsTheWalBeforeCopyingIt() {
        val database = openDatabaseWithAPendingWal()
        try {
            runFullBackup(BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED)
        } finally {
            database.close()
        }

        // wal_checkpoint(TRUNCATE) folded the committed pages into the main file: without it the
        // copy taken for the payload would miss every transaction still living in the log.
        assertEquals(0L, walFile().length())
        assertTrue("the database itself must survive", databaseFile.length() > 0)
        assertFalse(
            "the payload is temporary and must not be left for the next backup to upload twice",
            databaseFilePayload().exists(),
        )
    }

    @Test
    fun cloudBackupWithoutClientSideEncryptionNeverTouchesTheDatabase() {
        val database = openDatabaseWithAPendingWal()
        try {
            assertTrue("the setup must leave work in the log", walFile().length() > 0)
            runFullBackup(0)
            assertTrue(
                "no CSE means no payload, so the database must not even be checkpointed",
                walFile().length() > 0,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun deviceTransferAlreadyCarriesTheDatabaseSoTheAgentAddsNothing() {
        val database = openDatabaseWithAPendingWal()
        try {
            runFullBackup(
                BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED or
                    BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER
            )
            assertTrue(walFile().length() > 0)
        } finally {
            database.close()
        }
        assertFalse(databaseFilePayload().exists())
    }

    @Test
    fun aPayloadLeftBehindByACrashedBackupIsDeletedBeforeTheFrameworkPass() {
        val stale = databaseFilePayload()
        databaseFile.parentFile?.mkdirs()
        stale.writeText("stale payload from an earlier attempt")

        runFullBackup(0)

        assertFalse(
            "a leftover payload would be inside the device-transfer rules and uploaded twice",
            stale.exists(),
        )
    }

    @Test
    fun restoringThePayloadReplacesTheDatabaseAndDropsTheSqliteSidecars() {
        databaseFile.parentFile?.mkdirs()
        databaseFile.writeText("previous database")
        val sidecars = listOf("-wal", "-shm", "-journal").map { suffix ->
            File(databaseFile.path + suffix).apply { writeText("sidecar") }
        }

        restorePayload(RESTORED_DATABASE)

        assertEquals(RESTORED_DATABASE, databaseFile.readText())
        for (sidecar in sidecars) {
            assertFalse(
                "a stale ${sidecar.name} would be replayed over the restored file on the next open",
                sidecar.exists(),
            )
        }
        assertFalse(
            "the restore scratch file must not survive the move",
            restoreScratchFile().exists(),
        )
    }

    @Test
    fun aCorruptPayloadLeavesTheDatabaseInPlaceAndClearsTheScratchFile() {
        databaseFile.parentFile?.mkdirs()
        databaseFile.writeText("previous database")
        val truncated = temporaryFolder.newFile("truncated.gz")
        val original = temporaryFolder.newFile("original.bin").apply {
            writeBytes("neurhome launch log; ".repeat(64 * 1024).toByteArray())
        }
        val whole = temporaryFolder.newFile("whole.gz")
        gzipTo(original, whole)
        truncated.writeBytes(whole.readBytes().copyOf(whole.length().toInt() / 2))

        restorePayloadFrom(truncated)

        assertEquals("previous database", databaseFile.readText())
        assertFalse(restoreScratchFile().exists())
    }

    @Test
    fun aRestoredFileWithAnotherNameIsLeftToTheFramework() {
        databaseFile.parentFile?.mkdirs()
        databaseFile.writeText("previous database")
        val sidecar = File(databaseFile.path + "-wal").apply { writeText("sidecar") }
        val elsewhere = File(temporaryFolder.newFolder("files"), "some_prefs.xml")

        val payload = gzipFile(RESTORED_DATABASE)
        val descriptor = ParcelFileDescriptor.open(payload, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            agent.onRestoreFile(
                descriptor,
                payload.length(),
                elsewhere,
                BackupAgent.TYPE_FILE,
                0L,
                0L,
            )
        } finally {
            descriptor.close()
        }

        assertEquals("previous database", databaseFile.readText())
        assertTrue("the sidecars belong to a payload restore only", sidecar.exists())
        assertFalse(restoreScratchFile().exists())
    }

    @Test
    fun finishingARestoreOfThePayloadForgetsTheCachedDatabase() {
        val cached = AppDatabase.getDatabase(context)

        restorePayload(RESTORED_DATABASE)
        agent.onRestoreFinished()

        assertNotSame(cached, AppDatabase.getDatabase(context))
    }

    @Test
    fun finishingARestoreWithoutThePayloadKeepsTheCachedDatabase() {
        val cached = AppDatabase.getDatabase(context)

        agent.onRestoreFinished()

        assertSame(cached, AppDatabase.getDatabase(context))
    }

    /** Opens the live database in WAL mode with a committed transaction still in the log. */
    private fun openDatabaseWithAPendingWal(): SQLiteDatabase {
        databaseFile.parentFile?.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(databaseFile, null).apply {
            enableWriteAheadLogging()
            execSQL("create table t (x)")
            execSQL("insert into t values (1)")
        }
    }

    private fun restorePayload(content: String) = restorePayloadFrom(gzipFile(content))

    private fun restorePayloadFrom(payload: File) {
        val descriptor = ParcelFileDescriptor.open(payload, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            agent.onRestoreFile(
                descriptor,
                payload.length(),
                databaseFilePayload(),
                BackupAgent.TYPE_FILE,
                0L,
                0L,
            )
        } finally {
            descriptor.close()
        }
    }

    /** Runs [NeurhomeBackupAgent.onFullBackup] against a transport output carrying [flags]. */
    private fun runFullBackup(flags: Int) {
        val descriptor = ParcelFileDescriptor.open(
            temporaryFolder.newFile("backup.out"),
            ParcelFileDescriptor.MODE_READ_WRITE,
        )
        try {
            agent.onFullBackup(fullBackupData(descriptor, flags))
        } finally {
            descriptor.close()
        }
    }

    private fun gzipFile(content: String): File {
        val source = temporaryFolder.newFile("source.bin").apply { writeText(content) }
        val payload = File(temporaryFolder.newFile("payload.gz").path).apply { delete() }
        gzipTo(source, payload)
        return payload
    }

    private fun databaseFilePayload() = File(databaseFile.parentFile, DATABASE_PAYLOAD_FILE_NAME)

    private fun restoreScratchFile() =
        File(databaseFile.parentFile, DATABASE_RESTORE_SCRATCH_FILE_NAME)

    private fun walFile() = File(databaseFile.path + "-wal")

    /**
     * A transport output carrying [flags]. The public SDK only exposes the no-argument constructor,
     * so the real one is reached reflectively; this is the object whose `getTransportFlags` decides
     * whether the database payload is added.
     */
    private fun fullBackupData(descriptor: ParcelFileDescriptor, flags: Int): FullBackupDataOutput {
        val constructor = FullBackupDataOutput::class.java.getDeclaredConstructor(
            ParcelFileDescriptor::class.java,
            Long::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
        constructor.isAccessible = true
        return constructor.newInstance(descriptor, 100L * 1024 * 1024, flags) as FullBackupDataOutput
    }

    private companion object {
        const val RESTORED_DATABASE = "database restored from the payload"
    }
}
