package ovh.litapp.neurhome3

import android.app.Application
import android.content.Context
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.VibratorManager
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import ovh.litapp.neurhome3.data.AppDatabase
import ovh.litapp.neurhome3.data.NeurhomeRepository

class NeurhomeApplication : Application() {
    // No need to cancel this scope as it'll be torn down with the process
    private val applicationScope = CoroutineScope(SupervisorJob())

    // Using by lazy so the database and the repository are only created when they're needed
    // rather than when the application starts
    private val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val repository by lazy {
        NeurhomeRepository(
            applicationLogEntryDao = database.applicationLogEntryDao(),
            hiddenPackageDao = database.hiddenPackageDao(),
            settingDao = database.settingDao(),
            packageManager = packageManager,
            application = this
        )
    }

    fun vibrate() {
        val effectId = VibrationEffect.Composition.PRIMITIVE_CLICK
        if (isPrimitiveSupported(effectId)) {
            vibratorManager.vibrate(
                CombinedVibration.createParallel(
                    VibrationEffect.startComposition().addPrimitive(effectId).compose()
                )
            )
        } else {
            Toast.makeText(
                this,
                "This primitive is not supported by this device.$effectId",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private val vibratorManager: VibratorManager by lazy {
        getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
    }

    private fun isPrimitiveSupported(effectId: Int): Boolean {
        return vibratorManager.defaultVibrator.areAllPrimitivesSupported(effectId)
    }
}