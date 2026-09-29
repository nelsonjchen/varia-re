package com.mindflakes.ridewatch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import com.mindflakes.ridewatch.core.BatteryStatus
import com.mindflakes.ridewatch.core.BluetoothAddress
import com.mindflakes.ridewatch.core.ReconnectPolicy
import java.util.UUID

/** One GATT owner; all state transitions serialized onto main looper. */
@SuppressLint("MissingPermission")
class BleRadar(private val context: Context, private val listener: Listener) {
    companion object {
        val SERVICE: UUID = UUID.fromString("6a4e3200-667b-11e3-949a-0800200c9a66")
        val MEASUREMENT: UUID = UUID.fromString("6a4e3203-667b-11e3-949a-0800200c9a66")
        val CCCD: UUID = uuid16("2902")

        fun uuid16(part: String): UUID = UUID.fromString("0000$part-0000-1000-8000-00805f9b34fb")
    }

    interface Listener {
        fun status(text: String)

        fun connected()

        fun disconnected()

        fun packet(bytes: ByteArray): Boolean

        fun battery(percent: Int)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var running = false
    private var subscribed = false
    private var address: String? = null
    private val reconnect = ReconnectPolicy()
    private var transportConnected = false
    private var observingBluetooth = false
    private val bluetoothState =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!running) return
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_TURNING_OFF,
                    BluetoothAdapter.STATE_OFF -> {
                        close()
                        handler.removeCallbacks(retryTask)
                        listener.disconnected()
                        listener.status("Bluetooth off · waiting for Bluetooth")
                    }
                    BluetoothAdapter.STATE_ON -> {
                        close()
                        handler.removeCallbacks(retryTask)
                        attempt()
                    }
                }
            }
        }
    private var lastPacket = 0L
    private var batteryReadPending = false
    private var batteryNotifyAttempted = false
    private var batteryNotifyPending = false
    private var batteryNotifications = false
    private val batteryPoll = Runnable { readBattery() }
    private val batteryTimeout = Runnable {
        if (running && (batteryReadPending || batteryNotifyPending))
            retry("Battery request timed out; reconnecting")
    }

    private fun readBattery() {
        val client = gatt ?: return
        if (!running || !subscribed || batteryReadPending || batteryNotifyPending) return
        val characteristic =
            client.getService(uuid16("180f"))?.getCharacteristic(uuid16("2a19")) ?: return
        try {
            batteryReadPending = client.readCharacteristic(characteristic)
            if (batteryReadPending) handler.postDelayed(batteryTimeout, 30_000)
            else enableBatteryNotifications()
            // Await the callback before another request: never overlap GATT operations.
            // Radar notifications/watchdog remain independent of battery-read success.
        } catch (error: RuntimeException) {
            batteryReadPending = false
            android.util.Log.w("RideWatchBle", "Battery read unavailable", error)
            enableBatteryNotifications()
        }
    }

    private fun scheduleBatteryRead() {
        handler.removeCallbacks(batteryPoll)
        if (running && subscribed && !batteryReadPending && !batteryNotifyPending)
            handler.postDelayed(batteryPoll, 60_000)
    }

    private fun enableBatteryNotifications() {
        if (batteryNotifyAttempted) {
            scheduleBatteryRead()
            return
        }
        batteryNotifyAttempted = true
        val client = gatt ?: return
        val characteristic = client.getService(uuid16("180f"))?.getCharacteristic(uuid16("2a19"))
        val descriptor = characteristic?.getDescriptor(CCCD)
        val cccd = characteristic?.let { BatteryStatus.cccdValue(it.properties) }
        android.util.Log.i(
            "RideWatchBle",
            "Battery properties=${characteristic?.properties} CCCD=$cccd",
        )
        if (characteristic == null || descriptor == null || cccd == null) {
            android.util.Log.i(
                "RideWatchBle",
                "Battery notifications unavailable; polling fallback",
            )
            scheduleBatteryRead()
            return
        }
        try {
            if (client.setCharacteristicNotification(characteristic, true)) {
                batteryNotifyPending =
                    client.writeDescriptor(
                        descriptor,
                        if (cccd == 2) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                        else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                    ) == BluetoothStatusCodes.SUCCESS
            }
        } catch (error: RuntimeException) {
            android.util.Log.w("RideWatchBle", "Battery subscription unavailable", error)
        }
        if (batteryNotifyPending) handler.postDelayed(batteryTimeout, 30_000)
        else scheduleBatteryRead()
    }

    private fun acceptBattery(bytes: ByteArray, source: String) {
        if (bytes.size == 1 && (bytes[0].toInt() and 255) <= 100) {
            val percent = bytes[0].toInt() and 255
            android.util.Log.d(
                "RideWatchBle",
                "Battery $source: raw=${bytes[0].toUByte().toString(16)} percent=$percent at=${SystemClock.elapsedRealtime()}",
            )
            listener.battery(percent)
        } else android.util.Log.w("RideWatchBle", "Invalid battery $source: bytes=${bytes.size}")
    }

    private val watchdog =
        object : Runnable {
            override fun run() {
                if (!running) return
                if (subscribed && SystemClock.elapsedRealtime() - lastPacket > 15000)
                    retry("No radar data; reconnecting")
                if (running && subscribed) handler.postDelayed(this, 5000)
            }
        }

    fun permissions(): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED

    fun start(savedAddress: String?) {
        stop()
        running = true
        try {
            address = BluetoothAddress.canonicalize(savedAddress)
        } catch (error: IllegalArgumentException) {
            running = false
            listener.status(error.message ?: "Invalid radar address")
            return
        }
        if (address != null && address != savedAddress)
            context.getSharedPreferences("ride", 0).edit().putString("radar", address).apply()
        reconnect.start()
        context.registerReceiver(
            bluetoothState,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )
        observingBluetooth = true
        attempt()
    }

    fun requestConnection() {
        if (running && !transportConnected) start(address)
    }

    fun stop() {
        running = false
        if (observingBluetooth) {
            context.unregisterReceiver(bluetoothState)
            observingBluetooth = false
        }
        handler.removeCallbacksAndMessages(null)
        close()
        // Intentional shutdown is handled by the service, not a transport-loss event.
    }

    private fun close() {
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(watchdog)
        transportConnected = false
        handler.removeCallbacks(batteryPoll)
        handler.removeCallbacks(batteryTimeout)
        batteryReadPending = false
        batteryNotifyAttempted = false
        batteryNotifyPending = false
        batteryNotifications = false
        subscribed = false
        scanner?.let {
            try {
                scanCallback?.let(it::stopScan)
            } catch (_: RuntimeException) {}
        }
        scanner = null
        scanCallback = null
        val old = gatt
        gatt = null
        if (old != null) {
            try {
                old.disconnect()
                old.close()
            } catch (_: RuntimeException) {}
        }
    }

    private fun attempt() {
        if (!running) return
        try {
            if (!permissions()) {
                listener.status("Bluetooth permission needed")
                return
            }
            val adapter = context.getSystemService(BluetoothManager::class.java).adapter
            if (adapter == null || !adapter.isEnabled) {
                listener.status("Bluetooth off · waiting for Bluetooth")
                return
            }
            val saved = address
            if (!saved.isNullOrBlank()) {
                listener.status(
                    if (reconnect.autoConnect) "Waiting for radar · auto-connect"
                    else "Connecting to saved radar…"
                )
                connect(adapter.getRemoteDevice(saved))
            } else {
                listener.status("Searching for radar…")
                scanner = adapter.bluetoothLeScanner
                val scan = newScanCallback()
                scanCallback = scan
                scanner?.startScan(
                    listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build()),
                    ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                    scan,
                )
                handler.postDelayed(timeout, 15000)
            }
        } catch (error: RuntimeException) {
            android.util.Log.e("RideWatchBle", "Bluetooth connection attempt failed", error)
            retry("Bluetooth unavailable: ${error.javaClass.simpleName}")
        }
    }

    private val timeout = Runnable { retry("Connection timed out; retrying") }

    private fun connect(device: BluetoothDevice) {
        scanCallback?.let { scanner?.stopScan(it) }
        scanner = null
        scanCallback = null
        handler.removeCallbacks(timeout)
        android.util.Log.i("RideWatchBle", "Connecting autoConnect=${reconnect.autoConnect}")
        gatt =
            device.connectGatt(
                context,
                reconnect.autoConnect,
                callback,
                BluetoothDevice.TRANSPORT_LE,
            )
        if (gatt == null) retry("Bluetooth client unavailable; retrying")
        else if (!reconnect.autoConnect) handler.postDelayed(timeout, 20000)
        // Pending auto-connect has no deadline: Android waits for advertisements.
    }

    private fun retry(message: String) {
        if (!running) return
        close()
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(retryTask)
        listener.disconnected()
        listener.status(message)
        val delay = reconnect.retryDelayMillis()
        android.util.Log.i("RideWatchBle", "$message; retry in ${delay}ms")
        handler.postDelayed(retryTask, delay)
    }

    private val retryTask = Runnable { attempt() }

    private fun newScanCallback(): ScanCallback =
        object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) {
                handler.post {
                    if (running && scanner != null && scanCallback === this) {
                        address = result.device.address
                        context
                            .getSharedPreferences("ride", 0)
                            .edit()
                            .putString("radar", address)
                            .apply()
                        connect(result.device)
                    }
                }
            }

            override fun onScanFailed(code: Int) {
                handler.post {
                    if (running && scanCallback === this) retry("Scan failed ($code); retrying")
                }
            }
        }

    private val callback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(client: BluetoothGatt, code: Int, state: Int) {
                handler.post {
                    if (client !== gatt || !running) return@post
                    if (
                        code == BluetoothGatt.GATT_SUCCESS &&
                            state == BluetoothProfile.STATE_CONNECTED
                    ) {
                        if (transportConnected) return@post
                        transportConnected = true
                        handler.removeCallbacks(timeout)
                        handler.postDelayed(timeout, 20000)
                        // Bound service discovery/subscription even for auto-connect.
                        // Track the transport immediately, including disconnect during discovery.
                        listener.connected()
                        listener.status("Reading radar services…")
                        if (!client.discoverServices()) retry("Service discovery failed")
                    } else if (
                        state == BluetoothProfile.STATE_DISCONNECTED ||
                            code != BluetoothGatt.GATT_SUCCESS
                    ) {
                        android.util.Log.i(
                            "RideWatchBle",
                            "Disconnected status=$code connected=$transportConnected",
                        )
                        if (reconnect.keepWaiting(transportConnected, code)) {
                            listener.status("Waiting for radar · auto-connect")
                        } else retry("Radar disconnected; retrying")
                    }
                }
            }

            override fun onServicesDiscovered(client: BluetoothGatt, code: Int) {
                handler.post {
                    if (client !== gatt || !running || !transportConnected) return@post
                    val measurement = client.getService(SERVICE)?.getCharacteristic(MEASUREMENT)
                    if (code != 0 || measurement == null) {
                        retry("Legacy radar service not found")
                        return@post
                    }
                    val descriptor = measurement.getDescriptor(CCCD)
                    if (
                        descriptor == null ||
                            !client.setCharacteristicNotification(measurement, true)
                    ) {
                        retry("Radar subscription unavailable")
                        return@post
                    }
                    if (
                        client.writeDescriptor(
                            descriptor,
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                        ) != BluetoothStatusCodes.SUCCESS
                    )
                        retry("Radar subscription failed")
                }
            }

            override fun onDescriptorWrite(
                client: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                code: Int,
            ) {
                handler.post {
                    if (client !== gatt || !running || !transportConnected) return@post
                    if (descriptor.characteristic.uuid == uuid16("2a19")) {
                        if (!batteryNotifyPending) return@post
                        handler.removeCallbacks(batteryTimeout)
                        batteryNotifyPending = false
                        batteryNotifications = code == BluetoothGatt.GATT_SUCCESS
                        android.util.Log.i(
                            "RideWatchBle",
                            "Battery notifications enabled=$batteryNotifications status=$code",
                        )
                        scheduleBatteryRead()
                        return@post
                    }
                    if (descriptor.characteristic.uuid != MEASUREMENT || descriptor.uuid != CCCD)
                        return@post
                    if (code != 0) {
                        retry("Subscription rejected ($code)")
                        return@post
                    }
                    handler.removeCallbacks(timeout)
                    subscribed = true
                    reconnect.ready()
                    handler.removeCallbacks(watchdog)
                    handler.postDelayed(watchdog, 5000)
                    lastPacket = SystemClock.elapsedRealtime()
                    listener.status("Connected · waiting for radar data")
                    readBattery()
                }
            }

            override fun onCharacteristicRead(
                client: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                bytes: ByteArray,
                code: Int,
            ) {
                val copy = bytes.copyOf()
                handler.post {
                    if (
                        client !== gatt ||
                            !running ||
                            !subscribed ||
                            characteristic.uuid != uuid16("2a19")
                    )
                        return@post
                    if (!batteryReadPending) return@post
                    handler.removeCallbacks(batteryTimeout)
                    batteryReadPending = false
                    if (code == BluetoothGatt.GATT_SUCCESS) acceptBattery(copy, "read")
                    else android.util.Log.w("RideWatchBle", "Battery read failed: status=$code")
                    enableBatteryNotifications()
                }
            }

            override fun onCharacteristicChanged(
                client: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                bytes: ByteArray,
            ) {
                val copy = bytes.copyOf()
                handler.post {
                    if (client !== gatt || !running || !subscribed) return@post
                    if (characteristic.uuid == uuid16("2a19")) {
                        acceptBattery(copy, "notification")
                        return@post
                    }
                    if (characteristic.uuid == MEASUREMENT && listener.packet(copy))
                        lastPacket = SystemClock.elapsedRealtime()
                }
            }
        }
}
