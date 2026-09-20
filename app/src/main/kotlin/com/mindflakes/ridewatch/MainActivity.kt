package com.mindflakes.ridewatch

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.le.*
import android.companion.*
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.os.*
import android.view.*
import android.widget.*
import com.mindflakes.ridewatch.core.*

class MainActivity : Activity() {
    private lateinit var headline: TextView
    private lateinit var detail: TextView
    private lateinit var battery: TextView
    private lateinit var radar: RadarView
    private var testAudio: RadarAudio? = null
    private var testVolume: RideVolume? = null
    private var testVibration: RadarVibration? = null
    private lateinit var rideButton: Button
    private lateinit var content: LinearLayout
    private var pendingMode: String? = null
    private var demo = false
    private var autoStartAttempted = false
    private var choosingRadar = false
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
        volumeControlStream = AudioManager.STREAM_ALARM
        autoStartAttempted = saved?.getBoolean("autoStartAttempted", false) ?: false
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
        val actions = LinearLayout(this).apply { setPadding(0, dp(10), 0, dp(4)) }
        rideButton =
            Button(this).apply {
                text = if (prefs.getBoolean("enabled", false)) "Pause" else "Resume"
                isAllCaps = false
                styleAction(this, true)
                setOnClickListener {
                    if (demo || prefs.getBoolean("enabled", false)) stopRide()
                    else start(resumeMode())
                }
            }
        setRideAction(if (prefs.getBoolean("enabled", false)) "Pause" else "Resume")
        actions.addView(
            rideButton,
            LinearLayout.LayoutParams(0, dp(56), 1.2f).apply { marginEnd = dp(10) },
        )
        val settings =
            Button(this).apply {
                text = "Controls"
                isAllCaps = false
                styleAction(this, false)
                setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_controls, 0, 0, 0)
                setOnClickListener { controls() }
            }
        actions.addView(settings, LinearLayout.LayoutParams(0, dp(56), 1f))
        content.addView(actions)
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            content.removeView(radar)
            content.addView(View(this), 2, LinearLayout.LayoutParams(-1, 0, 1f))
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

    private fun styleAction(button: Button, primary: Boolean) {
        button.apply {
            background =
                getDrawable(
                    if (primary) R.drawable.bg_ride_action else R.drawable.bg_controls_action
                )
            backgroundTintList = null
            stateListAnimator = null
            elevation = 0f
            minWidth = 0
            minimumWidth = 0
            minHeight = dp(56)
            setPadding(dp(if (primary) 16 else 12), 0, dp(if (primary) 16 else 12), 0)
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            compoundDrawablePadding = dp(8)
            setTextColor(Color.WHITE)
            compoundDrawableTintList = ColorStateList.valueOf(Color.WHITE)
        }
    }

    private fun setRideAction(label: String) {
        val resume = label == "Resume"
        rideButton.text = label
        rideButton.isActivated = resume
        val foreground = if (resume) INK else Color.WHITE
        rideButton.setTextColor(foreground)
        rideButton.compoundDrawableTintList = ColorStateList.valueOf(foreground)
        rideButton.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (resume) R.drawable.ic_resume else R.drawable.ic_pause,
            0,
            0,
            0,
        )
    }

    // stopService resolves the Intent component; it does not compare object identity.
    @SuppressLint("ImplicitSamInstance")
    private fun stopRide() {
        demo = false
        handler.removeCallbacksAndMessages(null)
        pendingMode = null
        testAudio?.stop()
        testVolume?.close()
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
            "Monitoring paused · Resume or reopen to connect",
            "DISCONNECTED",
            0,
            intArrayOf(),
            intArrayOf(),
            false,
        )
        setRideAction("Resume")
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
            .setNegativeButton("Volume") { _, _ -> volumeSettings() }
            .setPositiveButton("Done", null)
            .show()
    }

    private fun volumeSettings() {
        val manager = getSystemService(AudioManager::class.java)
        val panel =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(8), dp(24), dp(8))
            }
        panel.addView(
            TextView(this).apply {
                text =
                    "Tones and speech use alarm volume. This shares the volume of your wake-up alarms. Volume buttons adjust it while RideWatch is open."
            }
        )
        val automatic =
            Switch(this).apply {
                text = "Raise volume when starting a ride"
                minHeight = dp(56)
                isChecked = prefs.getBoolean("auto_volume", true)
            }
        panel.addView(automatic)
        val level = TextView(this)
        val slider =
            SeekBar(this).apply {
                max = 100
                progress = prefs.getInt("ride_volume", 80)
                minHeight = dp(48)
                contentDescription = "Minimum ride alarm volume"
                isEnabled = automatic.isChecked
            }
        fun label() {
            val steps = manager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            level.text =
                "Minimum: ${slider.progress}% · ${RideVolumePolicy.minimum(steps, slider.progress)} of $steps volume steps"
        }
        label()
        panel.addView(level)
        panel.addView(slider)
        automatic.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("auto_volume", checked).apply()
            slider.isEnabled = checked
        }
        slider.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    label()
                    if (fromUser) prefs.edit().putInt("ride_volume", progress).apply()
                }

                override fun onStartTrackingTouch(bar: SeekBar) {}

                override fun onStopTrackingTouch(bar: SeekBar) {}
            }
        )
        panel.addView(
            TextView(this).apply {
                text =
                    "Applies on the next ride or sound test. Pause restores the previous volume unless you adjusted it. Do Not Disturb and connected audio devices still apply."
            }
        )
        AlertDialog.Builder(this)
            .setTitle("Ride volume")
            .setView(ScrollView(this).apply { addView(panel) })
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
        val volume = testVolume ?: RideVolume(this).also { testVolume = it }
        val vibration = testVibration ?: RadarVibration(this).also { testVibration = it }
        val panel =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(8), dp(16), dp(8))
            }
        panel.addView(
            TextView(this).apply {
                text =
                    "Tap to replay. Tones and speech use alarm volume and your ride minimum; vibration follows your alert setting."
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
                    volume.start()
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
                    volume.start()
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
                    volume.start()
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
            volume.close()
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
                val message = intent.getStringExtra("detail") ?: ""
                val title =
                    when {
                        health == "DISCONNECTED" && message.startsWith("Searching for radar") ->
                            "Searching for radar"
                        health == "DISCONNECTED" &&
                            message.startsWith("Connecting to saved radar") -> "Connecting radar"
                        else -> intent.getStringExtra("title") ?: "Radar disconnected"
                    }
                render(
                    title,
                    message,
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
                setRideAction(if (prefs.getBoolean("enabled", false)) "Pause" else "Resume")
            }
        }

    override fun onStart() {
        super.onStart()
        registerReceiver(receiver, IntentFilter(RadarService.UPDATE), Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        // Opening the app is an explicit connection request, including after Pause.
        // Rotation and our permission/pairing flows are not a new opening.
        if (!autoStartAttempted && !choosingRadar) {
            autoStartAttempted = true
            if (!RadarService.active) {
                start(resumeMode())
                return
            }
        }
        if (prefs.getBoolean("paused", false) && !demo) showPaused()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pendingMode == null && !choosingRadar) autoStartAttempted = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("autoStartAttempted", autoStartAttempted)
        super.onSaveInstanceState(outState)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 10) choosingRadar = false
    }

    override fun onStop() {
        testAudio?.stop()
        testVolume?.close()
        testVibration?.cancel()
        unregisterReceiver(receiver)
        super.onStop()
    }

    private fun start(mode: String) {
        testAudio?.stop()
        testVolume?.close()
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
        if (results.isEmpty()) {
            pendingMode = null
            return
        }
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
                pendingMode = null
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
        choosingRadar = true
        try {
            startIntentSenderForResult(chooser, 10, null, 0, 0, 0)
        } catch (_: IntentSender.SendIntentException) {
            choosingRadar = false
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
        setRideAction("End preview")
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
        testVolume?.close()
        testVibration?.cancel()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
