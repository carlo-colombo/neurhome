package ovh.litapp.neurhome3.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import ovh.litapp.neurhome3.BuildConfig
import java.io.File

class NeurhomeFileProvider : FileProvider() {
    fun getDatabaseURI(c: Context): Uri {
        val exportFile = c.getDatabasePath(NEURHOME_DATABASE)

        return getFileUri(c, exportFile)
    }

    private fun getFileUri(c: Context, f: File): Uri {
        return getUriForFile(c, BuildConfig.APPLICATION_ID + ".provider", f)
    }
}