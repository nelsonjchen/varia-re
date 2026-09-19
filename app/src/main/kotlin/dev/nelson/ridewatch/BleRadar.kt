package dev.nelson.ridewatch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.*
import dev.nelson.ridewatch.core.BluetoothAddress
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
    private var running = false
    private var subscribed = false
    private var address: String? = null
    private var retries = 0
    private var lastPacket = 0L
    private val watchdog =
        object : Runnable {
            override fun run() {
                if (!running) return
                if (subscribed && SystemClock.elapsedRealtime() - lastPacket > 15000)
                    retry("No radar data; reconnecting")
                handler.postDelayed(this, 5000)
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
        retries = 0
        attempt()
        handler.postDelayed(watchdog, 5000)
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        close()
        // Intentional shutdown is handled by the service, not a transport-loss event.
    }

    private fun close() {
        subscribed = false
        scanner?.let {
            try {
                it.stopScan(scan)
            } catch (_: RuntimeException) {}
        }
        scanner = null
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
                retry("Turn on Bluetooth; retrying")
                return
            }
            val saved = address
            if (!saved.isNullOrBlank()) {
                listener.status("Connecting to saved radar…")
                connect(adapter.getRemoteDevice(saved))
            } else {
                listener.status("Searching for radar…")
                scanner = adapter.bluetoothLeScanner
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
        scanner?.stopScan(scan)
        scanner = null
        handler.removeCallbacks(timeout)
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        handler.postDelayed(timeout, 20000)
    }

    private fun retry(message: String) {
        if (!running) return
        close()
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(retryTask)
        listener.disconnected()
        listener.status(message)
        handler.postDelayed(retryTask, minOf(30000L, 1000L shl minOf(retries++, 5)))
    }

    private val retryTask = Runnable { attempt() }
    private val scan =
        object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) {
                handler.post {
                    if (running && scanner != null) {
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
                handler.post { retry("Scan failed ($code); retrying") }
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
                        // Track the transport immediately, including disconnect during discovery.
                        listener.connected()
                        listener.status("Reading radar services…")
                        if (!client.discoverServices()) retry("Service discovery failed")
                    } else if (
                        state == BluetoothProfile.STATE_DISCONNECTED ||
                            code != BluetoothGatt.GATT_SUCCESS
                    )
                        retry("Radar disconnected; retrying")
                }
            }

            override fun onServicesDiscovered(client: BluetoothGatt, code: Int) {
                handler.post {
                    if (client !== gatt || !running) return@post
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
                    if (client !== gatt || !running) return@post
                    if (code != 0) {
                        retry("Subscription rejected ($code)")
                        return@post
                    }
                    handler.removeCallbacks(timeout)
                    subscribed = true
                    retries = 0
                    lastPacket = SystemClock.elapsedRealtime()
                    listener.status("Connected · waiting for radar data")
                    client.getService(uuid16("180f"))?.getCharacteristic(uuid16("2a19"))?.let {
                        client.readCharacteristic(it)
                    }
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
                        client === gatt &&
                            code == 0 &&
                            characteristic.uuid == uuid16("2a19") &&
                            copy.isNotEmpty() &&
                            (copy[0].toInt() and 255) <= 100
                    )
                        listener.battery(copy[0].toInt() and 255)
                }
            }

            override fun onCharacteristicChanged(
                client: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                bytes: ByteArray,
            ) {
                val copy = bytes.copyOf()
                handler.post {
                    if (client !== gatt || !subscribed || characteristic.uuid != MEASUREMENT)
                        return@post
                    if (listener.packet(copy)) lastPacket = SystemClock.elapsedRealtime()
                }
            }
        }
}
