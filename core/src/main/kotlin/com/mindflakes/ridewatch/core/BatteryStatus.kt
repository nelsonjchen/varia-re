package com.mindflakes.ridewatch.core

/** Latest successful radar report, including unchanged values; time is monotonic. */
class BatteryStatus {
    var percent = -1
        private set

    private var sampledAt = -1L

    fun accept(value: Int, now: Long) {
        if (value !in 0..100 || now < 0) return
        percent = value
        sampledAt = now
    }

    fun ageMillis(now: Long): Long = if (sampledAt < 0) -1 else (now - sampledAt).coerceAtLeast(0)

    fun reset() {
        percent = -1
        sampledAt = -1
    }

    companion object {
        // GATT properties: NOTIFY=0x10, INDICATE=0x20. Prefer indications like Garmin.
        fun cccdValue(properties: Int): Int? =
            when {
                properties and 0x20 != 0 -> 2
                properties and 0x10 != 0 -> 1
                else -> null
            }
    }
}
