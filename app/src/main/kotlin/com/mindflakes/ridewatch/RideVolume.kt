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

    companion object {
        fun migrateSettings(context: Context) {
            val prefs = context.getSharedPreferences("ride", 0)
            if (!prefs.getBoolean("full_volume_v1", false))
                prefs
                    .edit()
                    .putInt("ride_volume", 100)
                    .putBoolean("auto_volume", true)
                    .putBoolean("full_volume_v1", true)
                    .commit()
        }
    }

    fun start(reapply: Boolean = false) {
        if (started) {
            if (reapply) applyMinimum()
            return
        }
        migrateSettings(context)
        started = true
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        // Recover the baseline only within the same boot.
        policy =
            if (ledger.getInt("boot", -2) == boot && ledger.contains("original")) {
                RideVolumePolicy(
                    ledger.getInt("original", current),
                    ledger.getInt("raised", current),
                    ledger.getBoolean("overridden", false),
                )
            } else RideVolumePolicy(current, current)
        applyMinimum()
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
    }

    private fun applyMinimum() {
        val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val minimum =
            if (settings.getBoolean("auto_volume", true))
                RideVolumePolicy.minimum(
                    manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                    settings.getInt("ride_volume", 100),
                )
            else current
        val next = policy!!.renewed(current, minimum)
        policy = next
        // Save before changing volume; preserve the baseline across repeated connections.
        ledger
            .edit()
            .clear()
            .putInt(
                "boot",
                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1),
            )
            .putInt("original", next.original)
            .putInt("raised", next.raised)
            .putBoolean("overridden", false)
            .commit()
        if (next.raised > current) setVolume(next.raised)
        next.observe(manager.getStreamVolume(AudioManager.STREAM_MUSIC))
        if (next.overridden) ledger.edit().putBoolean("overridden", true).apply()
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
