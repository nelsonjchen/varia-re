package dev.nelson.ridewatch

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.le.*
import android.companion.*
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.*
import android.view.*
import android.widget.*
import dev.nelson.ridewatch.core.*

class MainActivity : Activity() {
    private lateinit var headline: TextView
    private lateinit var detail: TextView
    private lateinit var battery: TextView
    private lateinit var radar: RadarView
    private var testAudio: RadarAudio? = null
    private var testVibration: RadarVibration? = null
    private lateinit var rideButton: Button
    private lateinit var content: LinearLayout
    private var pendingMode: String? = null
    private var demo = false
    private var autoStartAttempted = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences

    companion object {
        private val LIME = Color.rgb(210, 239, 108)
        private val INK = Color.rgb(16, 23, 19)
        private val MUTED = Color.rgb(164, 180, 170)
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        prefs = getSharedPreferences("ride", 0)
        content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.rgb(9, 13, 16))
            }
        setContentView(content)
        content.setOnApplyWindowInsetsListener { _, insets ->
            val safe =
                insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
            content.setPadding(
                safe.left + dp(16),
                safe.top + dp(8),
                safe.right + dp(16),
                safe.bottom + dp(8),
            )
            insets
        }
        text("RIDEWATCH", 16, Color.WHITE).letterSpacing = .16f
        battery = text("RVR315  ·  OFFLINE", 12, MUTED).apply { setPadding(0, dp(4), 0, dp(10)) }
        headline =
            text("Radar is off", 24, MUTED).apply {
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(Color.rgb(27, 35, 40))
            }
        radar = RadarView(this)
        content.addView(radar, LinearLayout.LayoutParams(-1, 0, 1f))
        detail =
            text("Pair your radar in Controls to get started.", 13, MUTED).apply {
                maxLines = 3
                setPadding(0, dp(6), 0, dp(6))
            }
        val actions = LinearLayout(this)
        rideButton =
            Button(this).apply {
                text = if (prefs.getBoolean("enabled", false)) "Pause" else "Resume"
                isAllCaps = false
                minHeight = dp(52)
                setOnClickListener {
                    if (demo || prefs.getBoolean("enabled", false)) stopRide()
                    else start(resumeMode())
                }
            }
        actions.addView(rideButton, LinearLayout.LayoutParams(0, -2, 1f))
        val settings =
            Button(this).apply {
                text = "Controls"
                isAllCaps = false
                minHeight = dp(52)
                setOnClickListener { controls() }
            }
        actions.addView(settings, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(actions)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            content.removeView(radar)
            content.addView(View(this), 3, LinearLayout.LayoutParams(-1, 0, 1f))
            content.setOnApplyWindowInsetsListener(null)
            content.setPadding(0, 0, dp(24), 0)
            val wide = LinearLayout(this).apply { setBackgroundColor(Color.rgb(9, 13, 16)) }
            (content.parent as ViewGroup).removeView(content)
            wide.addView(content, LinearLayout.LayoutParams(0, -1, 1f))
            wide.addView(radar, LinearLayout.LayoutParams(0, -1, 1f))
            setContentView(wide)
            wide.setOnApplyWindowInsetsListener { _, insets ->
                val safe =
                    insets.getInsets(
                        WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                    )
                wide.setPadding(
                    safe.left + dp(16),
                    safe.top + dp(8),
                    safe.right + dp(16),
                    safe.bottom + dp(8),
                )
                insets
            }
        }
    }

    // stopService resolves the Intent component; it does not compare object identity.
    @SuppressLint("ImplicitSamInstance")
    private fun stopRide() {
        demo = false
        handler.removeCallbacksAndMessages(null)
        pendingMode = null
        testAudio?.stop()
        testVibration?.cancel()
        prefs.edit().putBoolean("enabled", false).putBoolean("paused", true).apply()
        getSystemService(NotificationManager::class.java).cancel(2)
        stopService(Intent(this, RadarService::class.java))
        showPaused()
    }

    private fun resumeMode(): String =
        (prefs.getString("mode", "ride") ?: "ride").let { if (it == "motion") "ride" else it }

    private fun showPaused() {
        render(
            "Paused",
            "Radar disconnected · tap Resume when ready",
            "DISCONNECTED",
            0,
            intArrayOf(),
            intArrayOf(),
            false,
        )
        rideButton.text = "Resume"
        battery.text = "RVR315 · PAUSED"
    }

    private fun controls() {
        AlertDialog.Builder(this)
            .setTitle("Ride controls")
            .setItems(
                arrayOf(
                    "Pair radar",
                    "Sound & vibration",
                    "Host a group",
                    "Join a group",
                    "Preview radar display",
                    "Test alerts",
                )
            ) { _, which ->
                when (which) {
                    0 -> pair()
                    1 -> speechSettings()
                    2 -> hostGroup()
                    3 -> joinGroup()
                    4 -> preview()
                    5 -> testTones()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun speechSettings() {
        AlertDialog.Builder(this)
            .setTitle("Sound & vibration")
            .setMultiChoiceItems(
                arrayOf(
                    "Varia-style tones",
                    "Spoken vehicle alerts",
                    "Repeat estimated car speed",
                    "Vibration alerts",
                ),
                booleanArrayOf(
                    prefs.getBoolean("tones", true),
                    prefs.getBoolean("speech", true),
                    prefs.getBoolean("speed", false),
                    prefs.getBoolean("vibration", true),
                ),
            ) { _, which, checked ->
                prefs
                    .edit()
                    .putBoolean(arrayOf("tones", "speech", "speed", "vibration")[which], checked)
                    .apply()
            }
            .setNeutralButton("Test alerts") { _, _ -> testTones() }
            .setPositiveButton("Done", null)
            .show()
    }

    private fun testTones() {
        if (prefs.getBoolean("enabled", false)) {
            detail.text = "Stop monitoring before testing sounds."
            return
        }
        val audio = testAudio ?: RadarAudio(this).also { testAudio = it }
        val vibration = testVibration ?: RadarVibration(this).also { testVibration = it }
        val panel =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(8), dp(16), dp(8))
            }
        panel.addView(
            TextView(this).apply {
                text =
                    "Tap to replay. Tones use media volume; vibration follows your alert setting."
            }
        )
        val choices =
            listOf(
                "Connected · tone" to TonePolicy.Tone.CONNECTED,
                "Vehicle approaching · 2 pulses" to TonePolicy.Tone.APPROACH,
                "Fast approach · 3 long pulses" to TonePolicy.Tone.URGENT,
                "No vehicles detected · tone" to TonePolicy.Tone.EMPTY,
                "Radar data lost · long pulse" to TonePolicy.Tone.LOST,
            )
        for ((label, tone) in choices) panel.addView(
            Button(this).apply {
                text = label
                isAllCaps = false
                minHeight = dp(52)
                setOnClickListener {
                    audio.play(tone, null)
                    if (prefs.getBoolean("vibration", true)) vibration.play(tone)
                }
            },
            LinearLayout.LayoutParams(-1, -2),
        )
        panel.addView(
            Button(this).apply {
                text = "Spoken number · 35"
                isAllCaps = false
                setOnClickListener {
                    vibration.cancel()
                    audio.play(null, "35")
                }
            },
            LinearLayout.LayoutParams(-1, -2),
        )
        panel.addView(
            Button(this).apply {
                text = "Disconnect · sample battery 84%"
                isAllCaps = false
                setOnClickListener {
                    // Same policy and priority as a real connection loss; sample battery only.
                    val policy =
                        DisconnectPolicy().apply {
                            connected()
                            battery(84)
                        }
                    audio.play(TonePolicy.Tone.LOST, policy.disconnected(), true)
                    if (prefs.getBoolean("vibration", true)) vibration.play(TonePolicy.Tone.LOST)
                }
            },
            LinearLayout.LayoutParams(-1, -2),
        )
        val dialog =
            AlertDialog.Builder(this)
                .setTitle("Test alerts")
                .setView(ScrollView(this).apply { addView(panel) })
                .setNegativeButton("Close", null)
                .create()
        dialog.setOnDismissListener {
            audio.stop()
            vibration.cancel()
        }
        dialog.show()
    }

    private fun render(
        title: String,
        message: String,
        health: String,
        threat: Int,
        meters: IntArray,
        threats: IntArray,
        group: Boolean,
    ) {
        val color =
            when {
                health != "LIVE" -> MUTED
                threat == 2 -> Color.rgb(255, 104, 94)
                meters.isNotEmpty() -> Color.rgb(255, 196, 85)
                else -> LIME
            }
        headline.text = title
        headline.setTextColor(if (health == "LIVE") INK else color)
        headline.setBackgroundColor(if (health == "LIVE") color else Color.rgb(27, 35, 40))
        detail.text = message
        radar.update(health, meters, threats, color, group, demo)
        radar.keepScreenOn = demo || health == "LIVE"
    }

    private fun dp(value: Int): Int = Math.round(value * resources.displayMetrics.density)

    private fun text(value: String, size: Int, color: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            content.addView(this)
        }

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (demo) return
                if (prefs.getBoolean("paused", false)) {
                    showPaused()
                    return
                }
                val group = intent.getBooleanExtra("group", false)
                val health = intent.getStringExtra("health") ?: "DISCONNECTED"
                render(
                    intent.getStringExtra("title") ?: "Radar disconnected",
                    intent.getStringExtra("detail") ?: "",
                    health,
                    intent.getIntExtra("threat", 0),
                    intent.getIntArrayExtra("meters") ?: intArrayOf(),
                    intent.getIntArrayExtra("threats") ?: intArrayOf(),
                    group,
                )
                val level = intent.getIntExtra("battery", -1)
                battery.text =
                    (if (group) "GROUP · HOST RADAR" else "RVR315") +
                        if (level < 0) ""
                        else
                            "  ·  $level% ${if (health == "DISCONNECTED") "last known" else "at connection"}"
                rideButton.text = if (prefs.getBoolean("enabled", false)) "Pause" else "Resume"
            }
        }

    override fun onStart() {
        super.onStart()
        registerReceiver(receiver, IntentFilter(RadarService.UPDATE), Context.RECEIVER_NOT_EXPORTED)
        if (prefs.getBoolean("paused", false)) {
            if (!demo) showPaused()
            return
        }
        if (
            !autoStartAttempted &&
                !RadarService.active &&
                (prefs.contains("radar") || prefs.getBoolean("enabled", false))
        ) {
            autoStartAttempted = true
            start(resumeMode())
        }
    }

    override fun onStop() {
        testAudio?.stop()
        testVibration?.cancel()
        unregisterReceiver(receiver)
        super.onStop()
    }

    private fun start(mode: String) {
        testAudio?.stop()
        testVibration?.cancel()
        demo = false
        handler.removeCallbacksAndMessages(null)
        render(
            "Starting radar",
            "Waiting for connection",
            "WAITING",
            0,
            intArrayOf(),
            intArrayOf(),
            mode == "join",
        )
        battery.text = "RVR315 · CONNECTING"
        pendingMode = mode
        val needed =
            mutableListOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        if (mode != "join")
            needed +=
                listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
        needed += Manifest.permission.POST_NOTIFICATIONS
        needed.removeAll {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED &&
                !(it == Manifest.permission.ACCESS_COARSE_LOCATION &&
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED)
        }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), 20)
            return
        }
        launch(mode)
    }

    private fun launch(mode: String?) {
        if (mode == null) return
        pendingMode = null
        prefs.edit().putBoolean("paused", false).apply()
        try {
            startForegroundService(
                Intent(this, RadarService::class.java).setAction("start").putExtra("mode", mode)
            )
        } catch (error: RuntimeException) {
            detail.text = "Could not start: ${error.message}"
        }
    }

    override fun onRequestPermissionsResult(
        code: Int,
        permissions: Array<out String>,
        results: IntArray,
    ) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (results.isEmpty()) return
        if (code == 20) {
            val optional =
                setOf(
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            if (
                permissions.indices.any {
                    results[it] != PackageManager.PERMISSION_GRANTED && permissions[it] !in optional
                }
            ) {
                detail.text = "Required permission denied. Choose a mode again after granting it."
                return
            }
            launch(pendingMode)
        } else if (code == 21) {
            if (results.any { it != PackageManager.PERMISSION_GRANTED }) {
                detail.text = "Nearby devices permission is needed to pair and reconnect."
                return
            }
            pair()
        }
    }

    private fun pair() {
        if (
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
                PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) !=
                    PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
                21,
            )
            return
        }
        val manager = getSystemService(CompanionDeviceManager::class.java)
        val filter =
            BluetoothLeDeviceFilter.Builder()
                .setScanFilter(
                    ScanFilter.Builder().setServiceUuid(ParcelUuid(BleRadar.SERVICE)).build()
                )
                .build()
        val request =
            AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(false).build()
        val callback =
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(chooser: IntentSender) {
                    showChooser(chooser)
                }

                override fun onAssociationCreated(association: AssociationInfo) {
                    if (association.deviceMacAddress != null) saveRadar(association)
                }

                override fun onFailure(reason: CharSequence?) {
                    detail.text = "Pairing: $reason"
                }
            }
        try {
            manager.associate(request, mainExecutor, callback)
        } catch (error: RuntimeException) {
            detail.text = "Pairing unavailable: ${error.message}"
        }
    }

    private fun showChooser(chooser: IntentSender) {
        try {
            startIntentSenderForResult(chooser, 10, null, 0, 0, 0)
        } catch (_: IntentSender.SendIntentException) {
            detail.text = "Pairing chooser unavailable"
        }
    }

    private fun saveRadar(association: AssociationInfo) {
        prefs
            .edit()
            .putString(
                "radar",
                BluetoothAddress.canonicalize(association.deviceMacAddress?.toString()),
            )
            .putInt("association", association.id)
            .apply()
        // Testing build: save the association without enabling presence wake.
        detail.text = "Radar saved. Resume to connect."
        if (!prefs.getBoolean("paused", false)) start("ride")
    }

    private fun hostGroup() {
        val key = GroupCodec.newKey()
        AlertDialog.Builder(this)
            .setTitle("Host a ride group")
            .setMessage(
                "Use the same Wi-Fi or hotspot. Peers need this phone’s local IP address from network settings and this join code:\n\n$key\n\nA new code replaces the previous group. Keep the code within your group."
            )
            .setPositiveButton("Start & copy code") { _, _ ->
                prefs.edit().putString("key", key).apply()
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("RideWatch join code", key))
                start("host")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun joinGroup() {
        val fields =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(12), dp(24), 0)
            }
        val address =
            EditText(this).apply {
                hint = "Host IP · e.g. 192.168.43.1"
                setSingleLine()
                setText(prefs.getString("host", ""))
            }
        val key =
            EditText(this).apply {
                hint = "32-character join code"
                setSingleLine()
                setText(prefs.getString("key", ""))
            }
        fields.addView(address)
        fields.addView(key)
        AlertDialog.Builder(this)
            .setTitle("Join a local ride group")
            .setView(fields)
            .setMessage(
                "Keep both phone clocks automatic. A lost or delayed stream shows data lost."
            )
            .setPositiveButton("Join") { _, _ ->
                val value = key.text.toString().trim()
                try {
                    GroupCodec(value)
                } catch (error: IllegalArgumentException) {
                    detail.text = error.message
                    return@setPositiveButton
                }
                if (address.text.toString().trim().isEmpty()) {
                    detail.text = "Enter the host’s local IP"
                    return@setPositiveButton
                }
                prefs
                    .edit()
                    .putString("host", address.text.toString().trim())
                    .putString("key", value)
                    .apply()
                start("join")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun preview() {
        if (prefs.getBoolean("enabled", false)) {
            detail.text = "Stop monitoring before starting the simulated preview."
            return
        }
        demo = true
        rideButton.text = "End preview"
        handler.removeCallbacksAndMessages(null)
        battery.text = "SIMULATION  ·  NO RADAR CONNECTED"
        for (step in 0 until 14) handler.postDelayed(
            {
                if (demo) {
                    val distance = 112 - step * 8
                    val frame =
                        LegacyDecoder()
                            .decode(
                                byteArrayOf(
                                    ((step shl 4) or 2).toByte(),
                                    (if (step < 8) 0x81 else 0xc1).toByte(),
                                    distance.toByte(),
                                    80,
                                )
                            )
                    val target = frame.targets[0]
                    render(
                        if (step < 8) "Vehicles approaching" else "Fast approach",
                        "Simulated vehicles · distances behind you",
                        "LIVE",
                        target.threat,
                        intArrayOf(target.meters, minOf(150, distance + 38)),
                        intArrayOf(target.threat, 1),
                        false,
                    )
                }
            },
            step * 1000L,
        )
        handler.postDelayed(
            {
                if (demo)
                    render(
                        "Radar data lost",
                        "Preview ended · a lost stream is not an all-clear.",
                        "STALE",
                        0,
                        intArrayOf(),
                        intArrayOf(),
                        false,
                    )
            },
            15000,
        )
    }

    override fun onDestroy() {
        testAudio?.close()
        testVibration?.cancel()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
