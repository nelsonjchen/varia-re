package com.mindflakes.ridewatch.core

import java.io.*
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Versioned authenticated local-network packets. Join code is a random 128-bit AES key. */
class GroupCodec(hex: String?) {
    companion object {
        private val MAGIC = byteArrayOf(82, 87, 1)

        @JvmStatic
        fun newKey(): String =
            ByteArray(16)
                .also { SecureRandom().nextBytes(it) }
                .joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 255) }
    }

    @JvmRecord
    data class Message(
        val session: Long,
        val sequence: Long,
        val sentMs: Long,
        val join: Boolean,
        val snapshot: RadarTracker.Snapshot?,
    )

    private val key: ByteArray
    private val random = SecureRandom()

    init {
        require(hex != null && Regex("[0-9a-fA-F]{32}").matches(hex)) {
            "Join code must have 32 hex characters"
        }
        key = ByteArray(16) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    @Throws(Exception::class)
    fun encode(message: Message): ByteArray {
        val body = ByteArrayOutputStream()
        val out = DataOutputStream(body)
        out.writeLong(message.session)
        out.writeLong(message.sequence)
        out.writeLong(message.sentMs)
        out.writeBoolean(message.join)
        if (!message.join) {
            val snapshot = requireNotNull(message.snapshot)
            if (snapshot.targets.size > 64) throw IOException("Too many targets")
            out.writeByte(snapshot.health.ordinal)
            out.writeInt(snapshot.error)
            out.writeByte(snapshot.targets.size)
            for (target in snapshot.targets) {
                out.writeByte(target.id)
                out.writeByte(target.threat)
                out.writeByte(target.meters)
                out.writeDouble(target.speedMps)
            }
        }
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(MAGIC)
        return MAGIC + nonce + cipher.doFinal(body.toByteArray())
    }

    @Throws(Exception::class)
    fun decode(packet: ByteArray, wallMs: Long): Message {
        if (packet.size !in 56..1024 || !packet.copyOf(3).contentEquals(MAGIC))
            throw IOException("Packet shape")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, packet.copyOfRange(3, 15)),
        )
        cipher.updateAAD(MAGIC)
        val input =
            DataInputStream(
                ByteArrayInputStream(cipher.doFinal(packet.copyOfRange(15, packet.size)))
            )
        val session = input.readLong()
        val seq = input.readLong()
        val sent = input.readLong()
        val join = input.readBoolean()
        if (seq < 0 || sent > wallMs + 2000 || sent < wallMs - 3000)
            throw IOException("Expired packet or clocks differ")
        var snapshot: RadarTracker.Snapshot? = null
        if (!join) {
            val health = input.readUnsignedByte()
            val error = input.readInt()
            val count = input.readUnsignedByte()
            if (health >= RadarTracker.Health.entries.size || count > 64)
                throw IOException("Invalid state")
            val targets = mutableListOf<RadarFrame.Target>()
            val ids = mutableSetOf<Int>()
            repeat(count) {
                val id = input.readUnsignedByte()
                val threat = input.readUnsignedByte()
                val meters = input.readUnsignedByte()
                val speed = input.readDouble()
                if (
                    id > 63 ||
                        threat > 2 ||
                        meters !in 1..154 ||
                        !speed.isFinite() ||
                        speed !in 0.0..63.75 ||
                        !ids.add(id)
                )
                    throw IOException("Invalid target")
                targets += RadarFrame.Target(id, threat, meters, speed)
            }
            if (health != RadarTracker.Health.LIVE.ordinal && count != 0)
                throw IOException("Targets on unhealthy stream")
            snapshot =
                RadarTracker.Snapshot(RadarTracker.Health.entries[health], targets.toList(), error)
        }
        if (input.available() != 0) throw IOException("Trailing data")
        return Message(session, seq, sent, join, snapshot)
    }
}
