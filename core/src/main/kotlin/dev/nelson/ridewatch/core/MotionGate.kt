package dev.nelson.ridewatch.core

/** Requires sustained, recent, accurate movement; one GPS spike cannot start a ride. */
class MotionGate {
    companion object {
        const val START_MPS = 4 * .44704
    }

    private var aboveSince = -1L
    private var lastSample = -1L

    fun update(speedMps: Double, accuracyMps: Double, sampleMs: Long, nowMs: Long): Boolean {
        if (
            !speedMps.isFinite() ||
                !accuracyMps.isFinite() ||
                accuracyMps !in 0.0..1.0 ||
                sampleMs > nowMs ||
                nowMs - sampleMs > 10000 ||
                speedMps - accuracyMps <= START_MPS
        ) {
            aboveSince = -1
            return false
        }
        if (lastSample >= 0 && (sampleMs <= lastSample || sampleMs - lastSample > 4000))
            aboveSince = -1
        lastSample = sampleMs
        if (aboveSince < 0) aboveSince = sampleMs
        return sampleMs - aboveSince >= 5000
    }

    fun reset() {
        aboveSince = -1
        lastSample = -1
    }
}
