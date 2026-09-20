package com.mindflakes.ridewatch

import com.mindflakes.ridewatch.core.*
import java.net.*
import java.security.SecureRandom

/** LAN/hotspot-only prototype; one radar owner, up to eight authenticated listeners. */
class GroupLink(
    private val host: Boolean,
    private val hostAddress: String,
    key: String,
    private val listener: Listener,
) : AutoCloseable {
    companion object {
        const val PORT = 43157
    }

    interface Listener {
        fun snapshot(value: RadarTracker.Snapshot)

        fun status(text: String)
    }

    private val codec = GroupCodec(key)
    @Volatile private var running = false
    @Volatile
    private var latest = RadarTracker.Snapshot(RadarTracker.Health.DISCONNECTED, emptyList(), -1)
    @Volatile private var socket: DatagramSocket? = null
    private val session = SecureRandom().nextLong()

    fun publish(snapshot: RadarTracker.Snapshot) {
        latest = snapshot
    }

    fun start() {
        running = true
        Thread(::loop, "radar-group").start()
    }

    private fun loop() {
        val peers = mutableMapOf<SocketAddress, Long>()
        val sequences = mutableMapOf<String, Long>()
        var seq = 0L
        var nextSend = 0L
        try {
            val destination =
                if (host) null
                else {
                    val address = InetAddress.getByName(hostAddress)
                    require(
                        address.isSiteLocalAddress ||
                            address.isLinkLocalAddress ||
                            address.isLoopbackAddress
                    ) {
                        "Use a local Wi-Fi or hotspot address"
                    }
                    InetSocketAddress(address, PORT)
                }
            val transport =
                synchronized(this) {
                    if (!running) return
                    DatagramSocket(if (host) PORT else 0).also { socket = it }
                }
            transport.soTimeout = 100
            while (running) {
                val now = System.currentTimeMillis()
                if (now >= nextSend) {
                    val bytes =
                        codec.encode(
                            GroupCodec.Message(
                                session,
                                seq++,
                                now,
                                !host,
                                if (host) latest else null,
                            )
                        )
                    if (host) {
                        peers.entries.removeIf { now - it.value > 5000 }
                        peers.keys.forEach { transport.send(DatagramPacket(bytes, bytes.size, it)) }
                    } else transport.send(DatagramPacket(bytes, bytes.size, destination))
                    nextSend = now + 500
                }
                val buffer = ByteArray(1025)
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    transport.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                if (!host && packet.socketAddress != destination) continue
                try {
                    val message =
                        codec.decode(buffer.copyOf(packet.length), System.currentTimeMillis())
                    val identity = "${packet.socketAddress}:${message.session}"
                    if (message.sequence <= (sequences[identity] ?: -1L)) continue
                    if (sequences.size > 128) sequences.clear()
                    sequences[identity] = message.sequence
                    if (host && message.join && (packet.socketAddress in peers || peers.size < 8))
                        peers[packet.socketAddress] = System.currentTimeMillis()
                    else if (!host && !message.join) message.snapshot?.let(listener::snapshot)
                } catch (_: Exception) {
                    /* Invalid input never updates health. */
                }
            }
        } catch (error: Exception) {
            if (running) listener.status("Group connection unavailable: ${error.message}")
        } finally {
            socket?.close()
        }
    }

    @Synchronized
    override fun close() {
        running = false
        socket?.close()
    }
}
