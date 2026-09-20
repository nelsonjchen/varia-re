package com.mindflakes.ridewatch

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.mindflakes.ridewatch.core.RideVolumePolicy

/** One volume lease per monitoring session (or paused sound preview). */
class RideVolume(private val context: Context) : AutoCloseable {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val settings = context.getSharedPreferences("ride", 0)
    private val ledger = context.getSharedPreferences("ride_media_volume_session", 0)
    private var policy: RideVolumePolicy? = null
    private var started = false
    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                policy?.let {
                    it.observe(manager.getStreamVolume(AudioManager.STREAM_MUSIC))
                    if (it.overridden) ledger.edit().putBoolean("overridden", true).apply()
                }
            }
        }

    fun start() {
        if (started) return
        started = true
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        // Recover a killed service's lease, but never carry volume ownership across reboot.
        if (ledger.getInt("boot", -2) == boot && ledger.contains("original")) {
            policy =
                RideVolumePolicy(
                    ledger.getInt("original", current),
                    ledger.getInt("raised", current),
                    ledger.getBoolean("overridden", false),
                )
            policy!!.observe(current)
        } else {
            ledger.edit().clear().commit()
            val target =
                if (settings.getBoolean("auto_volume", true))
                    maxOf(
                        current,
                        RideVolumePolicy.minimum(
                            manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                            settings.getInt("ride_volume", 80),
                        ),
                    )
                else current
            policy = RideVolumePolicy(current, target)
            // Persist before changing a global setting so process death cannot lose the baseline.
            ledger
                .edit()
                .putInt("boot", boot)
                .putInt("original", current)
                .putInt("raised", target)
                .commit()
            if (target > current) setVolume(target)
            policy!!.observe(manager.getStreamVolume(AudioManager.STREAM_MUSIC))
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
    }

    private fun setVolume(value: Int) {
        try {
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
        } catch (error: SecurityException) {
            Log.w("RideWatchAudio", "Media volume restricted by system policy", error)
        }
    }

    override fun close() {
        if (!started) return
        started = false
        context.contentResolver.unregisterContentObserver(observer)
        policy?.restore(manager.getStreamVolume(AudioManager.STREAM_MUSIC))?.let { setVolume(it) }
        ledger.edit().clear().commit()
        policy = null
    }
}
