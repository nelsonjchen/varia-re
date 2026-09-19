package dev.nelson.ridewatch.core

/** Tracks transport separately from packet freshness, including loss before the first frame. */
class DisconnectPolicy {
    private var connected = false
    var lastBattery = -1
        private set

    fun connected() {
        connected = true
    }

    fun battery(percent: Int) {
        if (percent in 0..100) lastBattery = percent
    }

    fun disconnected(): String? {
        if (!connected) return null
        connected = false
        return if (lastBattery >= 0) "Disconnected. Last battery $lastBattery percent"
        else "Radar disconnected"
    }

    /** Intentional pause/mode change must not announce a fault or leak another radar's battery. */
    fun reset() {
        connected = false
        lastBattery = -1
    }
}
