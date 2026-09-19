package dev.nelson.ridewatch

import android.content.Context
import android.os.*
import dev.nelson.ridewatch.core.TonePolicy.Tone

/** Finite, event-based patterns; never a continuous vibration loop. */
class RadarVibration(context: Context) {
    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    private val attributes =
        VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_NOTIFICATION).build()

    fun play(tone: Tone?) {
        cancel()
        if (!vibrator.hasVibrator()) return
        val timings =
            when (tone) {
                Tone.APPROACH -> longArrayOf(0, 180, 120, 180)
                Tone.URGENT -> longArrayOf(0, 300, 100, 300, 100, 300)
                Tone.LOST -> longArrayOf(0, 650)
                else -> return
            }
        vibrator.vibrate(VibrationEffect.createWaveform(timings, -1), attributes)
    }

    fun cancel() {
        vibrator.cancel()
    }
}
