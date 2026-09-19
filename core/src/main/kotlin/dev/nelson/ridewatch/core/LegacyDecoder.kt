package dev.nelson.ridewatch.core

/** Independently implemented from Varia 5.1.3 static evidence; see protocol report. */
class LegacyDecoder {
    fun decode(bytes: ByteArray?): RadarFrame {
        require(bytes != null && bytes.isNotEmpty()) { "Empty frame" }
        val header = bytes[0].toInt() and 255
        if (header and 1 != 0) {
            require(bytes.size == 2) { "Invalid error frame" }
            return RadarFrame(
                RadarFrame.Kind.ERROR,
                header ushr 4,
                bytes[1].toInt() and 255,
                emptyList(),
            )
        }
        // Diagnostic packets must not keep the measurement stream alive.
        if (header == 6 && bytes.size == 6)
            return RadarFrame(RadarFrame.Kind.UNSUPPORTED, header ushr 4, -1, emptyList())
        require((bytes.size - 1) % 3 == 0 && bytes.size <= 244) { "Invalid target frame length" }
        val targets = mutableListOf<RadarFrame.Target>()
        for (offset in 1 until bytes.size step 3) {
            val flags = bytes[offset].toInt() and 255
            val range = bytes[offset + 1].toInt() and 255
            if (range !in 1..154) continue
            val threat =
                when (flags ushr 6) {
                    3 -> 2
                    2 -> 1
                    else -> 0
                }
            targets +=
                RadarFrame.Target(
                    flags and 63,
                    threat,
                    range,
                    (bytes[offset + 2].toInt() and 255) * .25,
                )
        }
        return RadarFrame(RadarFrame.Kind.MEASUREMENT, header ushr 4, -1, targets)
    }
}
