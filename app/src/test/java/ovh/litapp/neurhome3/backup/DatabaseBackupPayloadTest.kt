package ovh.litapp.neurhome3.backup

import android.app.backup.BackupAgent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * JVM tests for the pure parts of the database backup payload. The transport-flag decision is
 * the code that replaces the XML `requireFlags="clientSideEncryption"` enforcement once a custom
 * agent owns the payload, so it is pinned here; the gzip helpers are exercised with a real
 * round-trip through a temp directory.
 */
class DatabaseBackupPayloadTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun uploadsThePayloadForCloudBackupWithClientSideEncryption() {
        assertTrue(
            shouldUploadDatabasePayload(BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED)
        )
    }

    @Test
    fun skipsThePayloadWithoutClientSideEncryption() {
        assertFalse(shouldUploadDatabasePayload(0))
    }

    @Test
    fun skipsThePayloadForDeviceToDeviceTransfer() {
        assertFalse(
            shouldUploadDatabasePayload(BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER)
        )
    }

    @Test
    fun skipsThePayloadWhenADeviceTransferAlsoAdvertisesEncryption() {
        assertFalse(
            shouldUploadDatabasePayload(
                BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED or
                    BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER
            )
        )
    }

    @Test
    fun derivesThePayloadPathNextToTheDatabase() {
        val databases = temporaryFolder.newFolder("databases")
        val databaseFile = File(databases, "neurhome_database")

        val payload = databasePayloadFile(databaseFile)

        assertEquals(databases, payload.parentFile)
        assertEquals(DATABASE_PAYLOAD_FILE_NAME, payload.name)
        assertTrue(isDatabasePayloadFile(payload))
        assertFalse(isDatabasePayloadFile(databaseFile))
        assertFalse(isDatabasePayloadFile(File(databases, "neurhome_database-wal")))
    }

    @Test
    fun gzipRoundTripRestoresTheExactBytes() {
        val databases = temporaryFolder.newFolder("databases")
        val databaseFile = File(databases, "neurhome_database")
        val original = "neurhome launch log; ".repeat(4096).toByteArray()
        databaseFile.writeBytes(original)
        val payload = databasePayloadFile(databaseFile)

        gzipTo(databaseFile, payload)

        val header = ByteArray(2)
        payload.inputStream().use { it.read(header) }
        assertArrayEquals(byteArrayOf(0x1f, 0x8b.toByte()), header)
        assertTrue(
            "the compressed payload should be smaller than the database: " +
                "payload=${payload.length()} database=${databaseFile.length()}",
            payload.length() < databaseFile.length()
        )

        val restored = File(databases, "restored")
        gunzipTo(payload, restored)

        assertArrayEquals(original, restored.readBytes())
    }

    @Test
    fun gzipRoundTripHandlesAnEmptyDatabase() {
        val databases = temporaryFolder.newFolder("databases")
        val databaseFile = File(databases, "neurhome_database").apply { writeBytes(ByteArray(0)) }
        val payload = databasePayloadFile(databaseFile)

        gzipTo(databaseFile, payload)
        val restored = File(databases, "restored")
        gunzipTo(payload, restored)

        assertArrayEquals(ByteArray(0), restored.readBytes())
    }

    @Test
    fun deletesDatabaseSidecarsButKeepsTheDatabase() {
        val databases = temporaryFolder.newFolder("databases")
        val databaseFile = File(databases, "neurhome_database").apply { writeText("db") }
        val sidecars = listOf("-wal", "-shm", "-journal").map { suffix ->
            File(databaseFile.path + suffix).apply { writeText("sidecar") }
        }

        deleteDatabaseSidecars(databaseFile)

        assertTrue("the database itself must survive", databaseFile.isFile)
        for (sidecar in sidecars) {
            assertFalse("${sidecar.name} must be deleted", sidecar.exists())
        }
    }
}
