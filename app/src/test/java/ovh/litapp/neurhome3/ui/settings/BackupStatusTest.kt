package ovh.litapp.neurhome3.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupStatusTest {
    @Test
    fun secureDeviceUnderQuotaWillBackUp() {
        val status = evaluateBackupStatus(facts(hasSecureLockScreen = true, payloadBytes = 1_000_000))

        assertEquals(CloudBackupAvailability.WILL_RUN, status.availability)
        assertFalse(status.exceedsQuota)
        assertFalse(status.nearQuota)
        assertTrue(status.headline.contains("will run"))
    }

    @Test
    fun missingSecureLockScreenSkipsCloudBackupAndExplainsWhy() {
        val status = evaluateBackupStatus(facts(hasSecureLockScreen = false, payloadBytes = 1_000_000))

        assertEquals(CloudBackupAvailability.SKIPPED_NO_SECURE_LOCK_SCREEN, status.availability)
        assertTrue(
            "the status must name the missing screen lock, not show a generic off state",
            status.headline.contains("no screen lock")
        )
        assertTrue("the status must say the backup is skipped", status.headline.contains("skipped"))
    }

    @Test
    fun payloadOverQuotaSkipsCloudBackupEvenWithSecureLockScreen() {
        val status = evaluateBackupStatus(
            facts(hasSecureLockScreen = true, payloadBytes = CLOUD_BACKUP_QUOTA_BYTES + 1)
        )

        assertEquals(CloudBackupAvailability.SKIPPED_OVER_QUOTA, status.availability)
        assertTrue(status.exceedsQuota)
        assertFalse(status.nearQuota)
        assertTrue(status.headline.contains("skipped"))
        assertTrue(status.sizeSummary.contains("Over the limit, so cloud backup is skipped."))
    }

    @Test
    fun payloadNearQuotaIsWarnedButStillBacksUp() {
        val nearQuota = CLOUD_BACKUP_QUOTA_BYTES * 9 / 10
        val status = evaluateBackupStatus(facts(hasSecureLockScreen = true, payloadBytes = nearQuota))

        assertEquals(CloudBackupAvailability.WILL_RUN, status.availability)
        assertFalse(status.exceedsQuota)
        assertTrue(status.nearQuota)
        assertTrue(status.sizeSummary.contains("Close to the limit"))
    }

    @Test
    fun payloadExactlyAtQuotaStillFits() {
        val status = evaluateBackupStatus(
            facts(hasSecureLockScreen = true, payloadBytes = CLOUD_BACKUP_QUOTA_BYTES)
        )

        assertEquals(CloudBackupAvailability.WILL_RUN, status.availability)
        assertFalse(status.exceedsQuota)
    }

    @Test
    fun sizeSummaryFormatsBytesWithUnitAndQuota() {
        val status = evaluateBackupStatus(facts(hasSecureLockScreen = true, payloadBytes = 512 * 1024))

        assertTrue(status.sizeSummary.contains("512 KB"))
        assertTrue(status.sizeSummary.contains("25.0 MB"))
    }

    private fun facts(hasSecureLockScreen: Boolean, payloadBytes: Long): BackupFacts =
        object : BackupFacts {
            override val hasSecureLockScreen: Boolean = hasSecureLockScreen
            override val payloadBytes: Long = payloadBytes
        }
}
