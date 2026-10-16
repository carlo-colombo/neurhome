package ovh.litapp.neurhome3.ui.settings

import java.util.Locale

/**
 * Android Auto Backup gives every app 25 MB of cloud backup storage per app user and refuses to
 * run the backup once the dataset is larger. The limit is documented, not configurable, and it
 * applies to cloud backup only; device-to-device transfer has no such cap.
 * Source: https://developer.android.com/guide/topics/data/autobackup ("Each app is limited to
 * 25 MB of backup data per app user").
 */
const val CLOUD_BACKUP_QUOTA_BYTES: Long = 25L * 1024 * 1024

/** Warn once the payload reaches this fraction (80%) of [CLOUD_BACKUP_QUOTA_BYTES]. */
private const val QUOTA_WARN_NUMERATOR = 4L
private const val QUOTA_WARN_DENOMINATOR = 5L

/** Whether Android will run cloud backup for this app at all. */
enum class CloudBackupAvailability {
    /** Client-side encryption is available, so cloud backup will run. */
    WILL_RUN,

    /** No secure lock screen, so mandatory client-side encryption is unavailable and backup is skipped. */
    SKIPPED_NO_SECURE_LOCK_SCREEN,

    /** The payload is larger than the 25 MB cloud backup cap, so the backup is skipped. */
    SKIPPED_OVER_QUOTA,
}

/**
 * The context-dependent facts the status needs. Injected as an interface so [evaluateBackupStatus]
 * stays free of Android types and is unit-testable without a device.
 */
interface BackupFacts {
    /** `KeyguardManager.isDeviceSecure`: false means client-side encryption cannot be used. */
    val hasSecureLockScreen: Boolean

    /** Bytes of `neurhome_database` plus its `-wal` and `-shm` sidecars. */
    val payloadBytes: Long
}

/**
 * Pure status of the automatic cloud backup. It deliberately carries no time or server-side
 * state: Android exposes no API for either, so a "last backed up at ..." claim is impossible.
 */
data class BackupStatus(
    val availability: CloudBackupAvailability,
    val payloadBytes: Long,
) {
    val quotaBytes: Long get() = CLOUD_BACKUP_QUOTA_BYTES

    val exceedsQuota: Boolean get() = payloadBytes > CLOUD_BACKUP_QUOTA_BYTES

    val nearQuota: Boolean
        get() = !exceedsQuota &&
            payloadBytes >= CLOUD_BACKUP_QUOTA_BYTES * QUOTA_WARN_NUMERATOR / QUOTA_WARN_DENOMINATOR

    val headline: String
        get() = when (availability) {
            CloudBackupAvailability.WILL_RUN -> "Cloud backup will run"
            CloudBackupAvailability.SKIPPED_NO_SECURE_LOCK_SCREEN ->
                "Cloud backup is skipped: this device has no screen lock"
            CloudBackupAvailability.SKIPPED_OVER_QUOTA ->
                "Cloud backup is skipped: the data is over the ${formatBytes(quotaBytes)} limit"
        }

    val sizeSummary: String
        get() = buildString {
            append("Data on this device: ")
            append(formatBytes(payloadBytes))
            append(" of the ")
            append(formatBytes(quotaBytes))
            append(" cloud backup limit.")
            when {
                exceedsQuota -> append(" Over the limit, so cloud backup is skipped.")
                nearQuota -> append(" Close to the limit; if it grows past it, cloud backup stops.")
            }
        }
}

/** Decides whether cloud backup will run and how the payload sits against the quota. */
fun evaluateBackupStatus(facts: BackupFacts): BackupStatus = BackupStatus(
    availability = when {
        !facts.hasSecureLockScreen -> CloudBackupAvailability.SKIPPED_NO_SECURE_LOCK_SCREEN
        facts.payloadBytes > CLOUD_BACKUP_QUOTA_BYTES -> CloudBackupAvailability.SKIPPED_OVER_QUOTA
        else -> CloudBackupAvailability.WILL_RUN
    },
    payloadBytes = facts.payloadBytes,
)

internal fun formatBytes(bytes: Long): String {
    val kib = 1024L
    val mib = kib * kib

    return when {
        bytes >= mib -> String.format(Locale.ROOT, "%.1f MB", bytes.toDouble() / mib)
        bytes >= kib -> String.format(Locale.ROOT, "%.0f KB", bytes.toDouble() / kib)
        else -> "$bytes B"
    }
}
