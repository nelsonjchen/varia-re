package com.mindflakes.ridewatch.core

class RadarFrame(
    @JvmField val kind: Kind,
    @JvmField val sequence: Int,
    @JvmField val error: Int,
    targets: List<Target>,
) {
    enum class Kind {
        MEASUREMENT,
        ERROR,
        UNSUPPORTED,
    }

    @JvmRecord
    data class Target(val id: Int, val threat: Int, val meters: Int, val speedMps: Double)

    @JvmField val targets: List<Target> = java.util.List.copyOf(targets)
}
