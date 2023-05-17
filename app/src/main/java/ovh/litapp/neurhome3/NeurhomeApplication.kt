package ovh.litapp.neurhome3

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.NeurhomeRepository

class NeurhomeApplication : Application() {
    // No need to cancel this scope as it'll be torn down with the process
    private val applicationScope = CoroutineScope(SupervisorJob())

    // Using by lazy so the database and the repository are only created when they're needed
    // rather than when the application starts
    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val repository by lazy {
        NeurhomeRepository(
            applicationLogEntryDao = database.applicationLogEntryDao(),
            packageManager = packageManager
        )
    }
}