package dev.nelson.ridewatch.core

/** Only speed and monotonic time are retained, never coordinates. */
class RiderSpeed {
    private var measuredAt = -1L
    private var speed = Double.NaN

    fun accept(metersPerSecond: Double, accuracyMps: Double, timestamp: Long) {
        if (timestamp < measuredAt) return
        measuredAt = timestamp
        speed =
            if (
                metersPerSecond.isFinite() &&
                    metersPerSecond >= 0 &&
                    accuracyMps.isFinite() &&
                    accuracyMps in 0.0..1.5
            )
                metersPerSecond
            else Double.NaN
    }

    fun at(now: Long): Double =
        if (measuredAt >= 0 && now >= measuredAt && now - measuredAt <= 3000) speed else Double.NaN

    /** User preference: unavailable rider speed contributes zero, keeping alerts speaking. */
    fun estimateAt(now: Long): Double = at(now).takeIf { it.isFinite() } ?: 0.0

    fun clear() {
        measuredAt = -1
        speed = Double.NaN
    }
}
