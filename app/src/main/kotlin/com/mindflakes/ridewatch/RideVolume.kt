package com.mindflakes.ridewatch

import android.content.Context
import android.database.ContentObserver
import android.media.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.mindflakes.ridewatch.core.RideVolumePolicy

/** One lease for the current audio route; never restore another output's volume baseline. */
class RideVolume(private val context: Context) : AutoCloseable {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val router = context.getSystemService(MediaRouter::class.java)
    private val settings = context.getSharedPreferences("ride", 0)
    private val ledger = context.getSharedPreferences("ride_media_volume_session", 0)
    private val handler = Handler(Looper.getMainLooper())
    private val attributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    private var policy: RideVolumePolicy? = null
    private var route = ""
    private var outputName = "Audio output"
    private var started = false
    private var lastVerifiedAt = -1L
    var onWarning: (String?) -> Unit = {}
    private var lastWarning: String? = null
    private val refresh = Runnable { verifyOutput() }
    private val readback = Runnable { reportVolume() }
    private val devices =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
                if (added.any { it.isSink }) outputChanged()
            }

            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
                if (removed.any { it.isSink }) outputChanged()
            }
        }
    private val routes =
        object : MediaRouter.SimpleCallback() {
            override fun onRouteSelected(
                router: MediaRouter,
                type: Int,
                info: MediaRouter.RouteInfo,
            ) = outputChanged()

            override fun onRouteUnselected(
                router: MediaRouter,
                type: Int,
                info: MediaRouter.RouteInfo,
            ) = outputChanged()

            override fun onRouteChanged(router: MediaRouter, info: MediaRouter.RouteInfo) =
                outputChanged()
        }
    private val observer =
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                if (!started) return
                val changed = checkRoute()
                if (!changed) {
                    policy?.observe(manager.getStreamVolume(AudioManager.STREAM_MUSIC))
                    if (policy?.overridden == true)
                        ledger.edit().putBoolean("overridden", true).apply()
                }
                reportVolume()
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

    private fun readRoute(): String {
        val outputs =
            try {
                manager.getAudioDevicesForAttributes(attributes)
            } catch (error: RuntimeException) {
                Log.w("RideWatchAudio", "Audio route unavailable", error)
                emptyList()
            }
        outputName =
            when (outputs.firstOrNull()?.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headphones"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth audio"
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
                AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing aid"
                null -> "Audio output unknown"
                else -> "External audio"
            }
        // IDs/types identify this route without storing names, addresses or user device labels.
        return outputs
            .sortedBy { it.id }
            .joinToString(",") { "${it.type}:${it.id}" }
            .ifEmpty { "unknown" }
    }

    fun start(reapply: Boolean = false) {
        if (started) {
            verifyOutput()
            if (reapply) applyMinimum()
            return
        }
        migrateSettings(context)
        started = true
        route = readRoute()
        val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        // Legacy ledgers have no route. Recover those only on the phone speaker.
        val savedRoute = ledger.getString("route", null)
        val matches = savedRoute == route || (savedRoute == null && outputName == "Phone speaker")
        policy =
            if (ledger.getInt("boot", -2) == boot && ledger.contains("original") && matches)
                RideVolumePolicy(
                    ledger.getInt("original", current),
                    ledger.getInt("raised", current),
                    ledger.getBoolean("overridden", false),
                )
            else RideVolumePolicy(current, current)
        applyMinimum()
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        manager.registerAudioDeviceCallback(devices, handler)
        router.addCallback(
            MediaRouter.ROUTE_TYPE_LIVE_AUDIO,
            routes,
            0,
        )
    }

    private fun outputChanged() {
        if (!started) return
        handler.removeCallbacks(refresh)
        handler.postDelayed(refresh, 400)
        handler.postDelayed(refresh, 2000)
    }

    /**
     * Check route and achieved level immediately before playback, including already-connected
     * switches.
     */
    fun verifyOutput(force: Boolean = true) {
        if (!started) return
        val now = SystemClock.elapsedRealtime()
        if (!force && lastVerifiedAt >= 0 && now - lastVerifiedAt < 5000) return
        lastVerifiedAt = now
        checkRoute()
        policy?.observe(manager.getStreamVolume(AudioManager.STREAM_MUSIC))
        if (policy?.overridden == true) ledger.edit().putBoolean("overridden", true).apply()
        reportVolume()
    }

    private fun checkRoute(): Boolean {
        val currentRoute = readRoute()
        if (currentRoute == route) return false
        route = currentRoute
        val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        policy = RideVolumePolicy(current, current)
        applyMinimum()
        return true
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
        ledger
            .edit()
            .clear()
            .putInt(
                "boot",
                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1),
            )
            .putString("route", route)
            .putInt("original", next.original)
            .putInt("raised", next.raised)
            .putBoolean("overridden", false)
            .commit()
        if (next.raised > current) setVolume(next.raised)
        next.observe(manager.getStreamVolume(AudioManager.STREAM_MUSIC))
        if (next.overridden) ledger.edit().putBoolean("overridden", true).apply()
        reportVolume()
        handler.removeCallbacks(readback)
        handler.postDelayed(readback, 200)
    }

    private fun reportVolume() {
        if (!started) return
        val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val minimum =
            if (settings.getBoolean("auto_volume", true))
                RideVolumePolicy.minimum(maximum, settings.getInt("ride_volume", 100))
            else 0
        val warning =
            when {
                manager.isStreamMute(AudioManager.STREAM_MUSIC) ->
                    "$outputName · media volume is muted"
                current < minimum ->
                    "$outputName · media volume $current/$maximum; target $minimum/$maximum"
                else -> null
            }
        if (warning != lastWarning) {
            lastWarning = warning
            if (warning != null) Log.w("RideWatchAudio", warning)
            onWarning(warning)
        }
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
        handler.removeCallbacksAndMessages(null)
        context.contentResolver.unregisterContentObserver(observer)
        manager.unregisterAudioDeviceCallback(devices)
        router.removeCallback(routes)
        // A route can change just before teardown. Do not apply the old route's baseline there.
        if (readRoute() == route)
            policy?.restore(manager.getStreamVolume(AudioManager.STREAM_MUSIC))?.let {
                setVolume(it)
            }
        ledger.edit().clear().commit()
        policy = null
        lastWarning = null
        onWarning(null)
    }
}
