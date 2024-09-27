package ovh.litapp.neurhome3.application

import android.media.AudioAttributes
import android.os.CombinedVibration
import android.os.VibrationAttributes
import android.os.VibrationEffect

fun NeurhomeApplication.vibrate() {
    val aa = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setUsage(AudioAttributes.USAGE_ALARM)
        .build()

    vibratorManager.vibrate(
        CombinedVibration.createParallel(
            VibrationEffect.createOneShot(
                50,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
        ), VibrationAttributes.Builder(aa).build()
    )
}