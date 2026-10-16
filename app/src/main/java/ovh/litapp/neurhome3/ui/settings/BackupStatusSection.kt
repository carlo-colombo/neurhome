package ovh.litapp.neurhome3.ui.settings

import android.app.KeyguardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ovh.litapp.neurhome3.data.NEURHOME_DATABASE
import java.io.File

/**
 * testTag for the read-only backup status panel. Exposed as a `resource-id` by the root
 * `testTagsAsResourceId` semantics in [ovh.litapp.neurhome3.MainActivity].
 */
const val BackupStatusSectionTestTag = "backup-status-section"

/**
 * Read-only backup status. Android exposes no last-backup time and no server-side backup state,
 * so this panel shows only facts derivable on the device: whether cloud backup will run, where
 * it goes, the payload size against the 25 MB cloud cap, and what is included.
 *
 * There is deliberately no "back up now" action: `BackupManager.dataChanged()` only marks data
 * as changed and lets Android schedule the work, so a button claiming to force a backup would
 * be dishonest.
 */
@Composable
fun BackupStatusSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val status = remember(context) { evaluateBackupStatus(readBackupFacts(context)) }
    val warningColor = MaterialTheme.colorScheme.error

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            .testTag(BackupStatusSectionTestTag)
    ) {
        Text(text = "Backup")
        Text(
            text = status.headline,
            color = if (status.availability == CloudBackupAvailability.WILL_RUN) {
                MaterialTheme.colorScheme.onSurface
            } else {
                warningColor
            }
        )
        Text(
            text = status.sizeSummary,
            style = MaterialTheme.typography.bodySmall,
            color = if (status.exceedsQuota) warningColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Goes to Google Drive, in your own Google account. Setting up a new phone also " +
                "copies the same data directly from this device (device-to-device transfer), " +
                "which is not subject to the cloud size limit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Includes app data and the launch log: the Wi-Fi network and SSID, the " +
                "location, and the search history you opted into logging.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Reads the two device facts. Kept here so [evaluateBackupStatus] can stay free of Android
 * imports and be unit-tested without a device.
 */
private fun readBackupFacts(context: Context): BackupFacts {
    val database = context.getDatabasePath(NEURHOME_DATABASE)
    val payloadBytes = listOf(
        database,
        File("${database.path}-wal"),
        File("${database.path}-shm")
    ).sumOf { if (it.isFile) it.length() else 0L }
    val keyguardManager = context.getSystemService(KeyguardManager::class.java)

    return object : BackupFacts {
        override val hasSecureLockScreen: Boolean = keyguardManager?.isDeviceSecure == true
        override val payloadBytes: Long = payloadBytes
    }
}
