package dev.nelson.ridewatch

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.*
import dev.nelson.ridewatch.core.RiderSpeed

/** Foreground ride-session speed. Does not record positions or wake a paused ride. */
class RideSpeedLocation(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val speed = RiderSpeed()
    private var callback: LocationCallback? = null
    private var requested = false
    private var message = "Precise location needed · pause/resume to enable speed"

    fun setEnabled(enabled: Boolean) {
        if (enabled && !requested) start() else if (!enabled && requested) stop()
    }

    @SuppressLint("MissingPermission")
    private fun start() {
        stop()
        requested = true
        message = "Waiting for accurate rider speed"
        val current =
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    if (callback !== this) return
                    for (location in result.locations) speed.accept(
                        if (location.hasSpeed()) location.speed.toDouble() else Double.NaN,
                        if (location.hasSpeedAccuracy())
                            location.speedAccuracyMetersPerSecond.toDouble()
                        else Double.NaN,
                        location.elapsedRealtimeNanos / 1_000_000,
                    )
                }
            }
        callback = current
        try {
            client
                .requestLocationUpdates(
                    LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                        .setMinUpdateIntervalMillis(1000)
                        .setMaxUpdateDelayMillis(0)
                        .setMaxUpdateAgeMillis(0)
                        .setWaitForAccurateLocation(true)
                        .build(),
                    current,
                    Looper.getMainLooper(),
                )
                .addOnFailureListener {
                    if (callback === current) {
                        stop()
                        requested = true
                        message = "Rider speed unavailable · pause/resume to retry"
                    }
                }
        } catch (_: RuntimeException) {
            stop()
            requested = true
            message = "Rider speed unavailable · pause/resume to retry"
        }
    }

    fun at(now: Long): Double = speed.estimateAt(now)

    fun description(): String {
        val value = speed.at(SystemClock.elapsedRealtime())
        return if (value.isFinite()) "Estimated car speed · rider ${Math.round(value / .44704)} mph"
        else "Rider speed assumed 0 · $message"
    }

    fun stop() {
        requested = false
        val previous = callback
        callback = null
        speed.clear()
        if (previous != null)
            try {
                client.removeLocationUpdates(previous)
            } catch (_: RuntimeException) {}
    }
}
