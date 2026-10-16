package ovh.litapp.neurhome3.data.repositories

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.litapp.neurhome3.data.AppDatabase

/**
 * Hands the current database file to an external app through the share sheet. Owns the
 * checkpoint that makes the exported file self-contained and the `FileProvider` URI that
 * exposes it, so neither the caller nor [NeurhomeRepository] has to know how the export
 * reaches the chooser.
 */
class DatabaseExporter(
    private val database: AppDatabase,
    private val scope: CoroutineScope,
    /** Resolves the shareable content URI for the exported database. */
    private val databaseUriProvider: (Context) -> Uri,
) {
    fun exportDatabase(context: Context) {
        scope.launch(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "application/octet-stream"

            database.query("pragma wal_checkpoint(full)", arrayOf()).use { cursor ->
                cursor.moveToFirst()
            }

            val uri = databaseUriProvider(context)

            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.flags = FLAG_GRANT_READ_URI_PERMISSION

            // The checkpoint and the FileProvider URI stay exactly as they are; only the launch
            // changes. It runs on a background dispatcher with a context that may be an
            // application context or an Activity that a configuration change has already
            // detached, so NEW_TASK is required and the main dispatcher keeps it off the IO
            // thread.
            val chooser = Intent.createChooser(intent, "Backup via:")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            withContext(Dispatchers.Main) { context.startActivity(chooser) }
        }
    }
}
