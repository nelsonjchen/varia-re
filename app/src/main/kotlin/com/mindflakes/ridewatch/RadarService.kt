package com.mindflakes.ridewatch

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.*
import android.location.*
import android.os.*
import com.mindflakes.ridewatch.core.*

class RadarService : Service() {
    companion object {
        @Volatile var active = false
        // Temporary testing profile: foreground sessions only, no external wake triggers.
        const val TESTING_LIFECYCLE = true
        const val UPDATE = "com.mindflakes.ridewatch.UPDATE"

        fun createChannel(context: Context) {
            context
                .getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        "ride",
                        "Radar monitoring",
                        NotificationManager.IMPORTANCE_LOW,
                    )
                )
        }

        fun headline(snapshot: RadarTracker.Snapshot): String =
            when (snapshot.health) {
                RadarTracker.Health.DISCONNECTED -> "Radar disconnected"
                RadarTracker.Health.WAITING -> "Waiting for radar data"
                RadarTracker.Health.STALE -> "Radar data lost"
                RadarTracker.Health.ERROR -> "Radar error ${snapshot.error}"
                RadarTracker.Health.LIVE ->
                    if (snapshot.targets.isEmpty()) "No targets detected"
                    else
                        "${snapshot.targets.size} vehicle${if (snapshot.targets.size == 1) "" else "s"} behind"
            }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tracker = RadarTracker()
    private val decoder = LegacyDecoder()
    private val alerts = AlertPolicy()
    private val motion = MotionGate()
    private val tones = TonePolicy()
    private val disconnect = DisconnectPolicy()
    private lateinit var ble: BleRadar
    private lateinit var audio: RadarAudio
    private lateinit var volume: RideVolume
    private lateinit var vibration: RadarVibration
    private lateinit var rideSpeed: RideSpeedLocation
    private var rideLocationAllowed = false
    private var radarConnected = false
    private var armed = false
    private var riding = false
    private val batteryStatus = BatteryStatus()
    private val battery: Int
        get() = batteryStatus.percent

    private var lastNotificationText: String? = null
    private var detail = "Ready"
    private var volumeWarning: String? = null
    private var mode = "ride"
    private var locations: LocationManager? = null
    private var group: GroupLink? = null
    private var groupGeneration = 0
    private var remote: RadarTracker.Snapshot? = null
    private var remoteAt = -1L
    private val prefs
        get() = getSharedPreferences("ride", 0)

    private val locationListener =
        object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (
                    armed &&
                        location.hasSpeed() &&
                        location.hasSpeedAccuracy() &&
                        motion.update(
                            location.speed.toDouble(),
                            location.speedAccuracyMetersPerSecond.toDouble(),
                            location.elapsedRealtimeNanos / 1_000_000,
                            SystemClock.elapsedRealtime(),
                        )
                ) {
                    armed = false
                    mode = "ride"
                    prefs.edit().putString("mode", mode).apply()
                    detail = "Movement detected · connecting"
                    beginRadar()
                }
            }

            override fun onProviderDisabled(provider: String) {
                if (armed) detail = "Location is off · waiting for movement"
            }

            override fun onProviderEnabled(provider: String) {}
        }

    override fun onCreate() {
        super.onCreate()
        active = true
        createChannel(this)
        volume =
            RideVolume(this).apply {
                onWarning = { warning ->
                    volumeWarning = warning
                    broadcast(tracker.snapshot(SystemClock.elapsedRealtime()))
                }
            }
        audio = RadarAudio(this, beforePlayback = { volume.verifyOutput() })
        vibration = RadarVibration(this)
        rideSpeed = RideSpeedLocation(this)
        ble =
            BleRadar(
                this,
                object : BleRadar.Listener {
                    override fun status(text: String) {
                        detail = text
                    }

                    override fun connected() {
                        volume.start(reapply = true)
                        radarConnected = true
                        tracker.connected()
                        disconnect.connected()
                        handler.removeCallbacks(tick)
                        handler.post(tick)
                    }

                    override fun disconnected() {
                        radarConnected = false
                        rideSpeed.stop()
                        tracker.reset()
                        val speech = disconnect.disconnected()
                        if (speech != null) {
                            // Actual transport loss is distinct from earlier stale-data warnings.
                            alerts.reset()
                            tones.reset()
                            if (prefs.getBoolean("vibration", true))
                                vibration.play(TonePolicy.Tone.LOST)
                            audio.play(
                                if (prefs.getBoolean("tones", true)) TonePolicy.Tone.LOST else null,
                                if (prefs.getBoolean("speech", true)) speech else null,
                                true,
                            )
                        }
                    }

                    override fun battery(percent: Int) {
                        batteryStatus.accept(percent, SystemClock.elapsedRealtime())
                        disconnect.battery(percent)
                    }

                    override fun packet(bytes: ByteArray): Boolean {
                        return try {
                            val frame = decoder.decode(bytes)
                            tracker.accept(frame, SystemClock.elapsedRealtime())
                            if (frame.kind == RadarFrame.Kind.MEASUREMENT)
                                detail = "Connected · receiving legacy radar measurements"
                            if (frame.kind == RadarFrame.Kind.UNSUPPORTED)
                                detail = "Unsupported radar packet · awaiting measurements"
                            frame.kind != RadarFrame.Kind.UNSUPPORTED
                        } catch (_: IllegalArgumentException) {
                            detail = "Malformed radar packet ignored"
                            false
                        }
                    }
                },
            )
    }

    private fun notification(text: String): Notification {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, RadarService::class.java).setAction("stop"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return Notification.Builder(this, "ride")
            .setSmallIcon(R.drawable.ic_radar)
            .setContentTitle("RideWatch")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Pause", stop).build())
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = if (intent == null) "resume" else intent.action
        if (action == "stop") {
            prefs.edit().putBoolean("enabled", false).putBoolean("paused", true).apply()
            getSystemService(NotificationManager::class.java).cancel(2)
            stopSelf()
            return START_NOT_STICKY
        }
        if (prefs.getBoolean("paused", false) || (TESTING_LIFECYCLE && action == "presence")) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (action == "resume" && !prefs.getBoolean("enabled", false)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (action == "connect_now" && (riding || mode == "join")) {
            volume.start(reapply = true)
            if (riding) ble.requestConnection()
            return START_STICKY
        }
        mode =
            (if (intent == null || action == "connect_now") prefs.getString("mode", "ride")
            else intent.getStringExtra("mode")) ?: "ride"
        if (TESTING_LIFECYCLE && mode == "motion") mode = "ride"
        armed = mode == "motion" && action != "presence"
        if (action == "presence" && mode == "motion") mode = "ride"
        var useRideLocation =
            mode != "join" &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        try {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (armed || useRideLocation)
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            try {
                startForeground(
                    1,
                    notification(
                        if (armed) "Armed · waiting for movement" else "Connecting radar…"
                    ),
                    types,
                )
            } catch (locationDenied: SecurityException) {
                if (!useRideLocation || armed) throw locationDenied
                useRideLocation = false
                startForeground(
                    1,
                    notification("Connecting radar…"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            }
        } catch (error: RuntimeException) {
            detail = "Could not start monitoring: ${error.javaClass.simpleName}"
            broadcast(tracker.snapshot(SystemClock.elapsedRealtime()))
            stopSelf()
            return START_NOT_STICKY
        }
        volume.start()
        prefs.edit().putBoolean("enabled", true).putString("mode", mode).apply()
        lastNotificationText = null
        handler.removeCallbacks(tick)
        disconnect.reset()
        radarConnected = false
        ble.stop()
        tracker.reset()
        batteryStatus.reset()
        riding = false
        alerts.reset()
        tones.reset()
        audio.stop()
        vibration.cancel()
        motion.reset()
        stopLocation()
        rideSpeed.stop()
        rideLocationAllowed = useRideLocation
        group?.close()
        group = null
        val generation = ++groupGeneration
        remote = null
        remoteAt = -1
        if (mode == "join" || mode == "host") {
            try {
                group =
                    GroupLink(
                            mode == "host",
                            prefs.getString("host", "") ?: "",
                            prefs.getString("key", "") ?: "",
                            object : GroupLink.Listener {
                                override fun snapshot(value: RadarTracker.Snapshot) {
                                    handler.post {
                                        if (generation == groupGeneration) {
                                            remote = value
                                            remoteAt = SystemClock.elapsedRealtime()
                                            detail =
                                                "Receiving host radar · distances relative to host"
                                        }
                                    }
                                }

                                override fun status(text: String) {
                                    handler.post {
                                        if (generation == groupGeneration) detail = text
                                    }
                                }
                            },
                        )
                        .also { it.start() }
            } catch (error: Exception) {
                detail = "Group setup failed: ${error.message}"
            }
        }
        if (mode == "join") {
            if (group != null) detail = "Joined mode · waiting for host"
        } else if (armed) startLocation() else beginRadar()
        handler.post(tick)
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        detail = "Armed · sustained movement above 4 mph starts radar"
        locations = getSystemService(LocationManager::class.java)
        if (
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            detail = "Precise location permission needed for motion mode"
            return
        }
        try {
            locations?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000,
                0f,
                locationListener,
                Looper.getMainLooper(),
            )
        } catch (_: RuntimeException) {
            detail = "Location unavailable · open app to retry"
        }
    }

    private fun stopLocation() {
        locations?.removeUpdates(locationListener)
        locations = null
    }

    private fun beginRadar() {
        if (riding) return
        riding = true
        stopLocation()
        ble.start(prefs.getString("radar", null))
    }

    private val tick =
        object : Runnable {
            override fun run() {
                val now = SystemClock.elapsedRealtime()
                volume.verifyOutput(force = false)
                rideSpeed.setEnabled(
                    radarConnected &&
                        rideLocationAllowed &&
                        prefs.getBoolean("speed", false) &&
                        prefs.getBoolean("speech", true) &&
                        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED
                )
                var snapshot = tracker.snapshot(now)
                if (mode == "join")
                    snapshot =
                        remote?.takeIf { now - remoteAt < 3000 }
                            ?: RadarTracker.Snapshot(RadarTracker.Health.STALE, emptyList(), -1)
                if (mode == "host") group?.publish(snapshot)
                if (
                    snapshot.health != RadarTracker.Health.LIVE ||
                        snapshot.targets.none { it.threat == 2 }
                )
                    audio.cancelPendingUrgent()
                val speech =
                    alerts.update(
                        snapshot,
                        now,
                        prefs.getBoolean("speed", false) && prefs.getBoolean("speech", true),
                        if (mode == "join") Double.NaN else rideSpeed.at(now),
                    )
                val tone = tones.update(snapshot)
                if (!prefs.getBoolean("vibration", true)) vibration.cancel()
                else if (tone != null) vibration.play(tone)
                if (speech != null || tone != null)
                    audio.play(
                        if (prefs.getBoolean("tones", true)) tone else null,
                        if (prefs.getBoolean("speech", true)) speech else null,
                        eventTone = tone,
                    )
                broadcast(snapshot)
                val notificationText =
                    volumeWarning?.let { "Alert volume · $it" }
                        ?: if (armed) detail else headline(snapshot)
                if (lastNotificationText != notificationText) {
                    getSystemService(NotificationManager::class.java)
                        .notify(1, notification(notificationText))
                    lastNotificationText = notificationText
                }
                handler.postDelayed(this, if (!radarConnected && mode == "ride") 5000 else 500)
            }
        }

    private fun broadcast(snapshot: RadarTracker.Snapshot) {
        val targets =
            snapshot.targets.joinToString("\n") {
                "${it.meters} m    ·    ${if (it.threat == 2) "Fast approach" else "Detected"}    ·    ${Math.round(it.speedMps / .44704)} mph radar speed"
            }
        val speedDetail =
            if (prefs.getBoolean("speed", false))
                "\n" +
                    (if (mode == "join") "Car speed unavailable · host speed not shared"
                    else if (!radarConnected) "Rider speed paused · waiting for radar"
                    else rideSpeed.description())
            else ""
        sendBroadcast(
            Intent(UPDATE)
                .setPackage(packageName)
                .putExtra("title", if (armed) "Ready for movement" else headline(snapshot))
                .putExtra(
                    "detail",
                    detail + speedDetail + (volumeWarning?.let { "\nAlert volume: $it" } ?: ""),
                )
                .putExtra("targets", targets)
                .putExtra("meters", snapshot.targets.map { it.meters }.toIntArray())
                .putExtra("threats", snapshot.targets.map { it.threat }.toIntArray())
                .putExtra("health", snapshot.health.name)
                .putExtra("threat", snapshot.targets.maxOfOrNull { it.threat } ?: 0)
                .putExtra("battery", battery)
                .putExtra("batteryAgeMs", batteryStatus.ageMillis(SystemClock.elapsedRealtime()))
                .putExtra("group", mode == "join")
        )
    }

    override fun onDestroy() {
        active = false
        groupGeneration++
        handler.removeCallbacksAndMessages(null)
        disconnect.reset()
        radarConnected = false
        ble.stop()
        stopLocation()
        rideSpeed.stop()
        group?.close()
        audio.close()
        volume.close()
        vibration.cancel()
        tracker.reset()
        detail = "Monitoring stopped"
        armed = false
        broadcast(tracker.snapshot(SystemClock.elapsedRealtime()))
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
