package ovh.litapp.neurhome3.application

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import java.io.File

/**
 * Bound on the debug-only logcat capture: `app_logcat.txt` is rotated every
 * [DEBUG_LOGCAT_ROTATE_KIB] KiB and [DEBUG_LOGCAT_ROTATED_FILES] rotated files are kept, so the
 * capture cannot grow for the whole lifetime of a debug install.
 */
private const val DEBUG_LOGCAT_ROTATE_KIB = 1024
private const val DEBUG_LOGCAT_ROTATED_FILES = 2

/**
 * Starts the debug launch/context capture. A `ContentProvider` is the only component the framework
 * instantiates at startup without user interaction, so declaring this one in the debug manifest
 * runs the capture in exactly the builds where `BuildConfig.DEBUG` used to gate it (`debug` build
 * type only) without a single line of it reaching the release APK: `NeurhomeApplication` must never
 * spawn a logcat process in production, where it would keep a process alive writing private data to
 * external storage and consume battery and space.
 */
class DebugLogcatCaptureProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val context = context ?: return true
        try {
            val logFile = File(context.getExternalFilesDir(null), "app_logcat.txt")
            // `-r`/`-n` are logcat's own rotation flags: the capture is bounded to
            // (DEBUG_LOGCAT_ROTATED_FILES) rotated files of DEBUG_LOGCAT_ROTATE_KIB plus the
            // active one, instead of appending until the device runs out of space.
            Runtime.getRuntime()
                .exec(
                    arrayOf(
                        "logcat", "-f", logFile.absolutePath, "-v", "time",
                        "-r", DEBUG_LOGCAT_ROTATE_KIB.toString(),
                        "-n", DEBUG_LOGCAT_ROTATED_FILES.toString(),
                    )
                )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
