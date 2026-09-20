package com.mindflakes.ridewatch.core

/** Event speech, or paced numeric estimates while targets remain live. */
class AlertPolicy {
    private val announced = mutableMapOf<Int, Int>()
    private var previousHealth = RadarTracker.Health.DISCONNECTED
    private var lastSpoken = -10000L
    private var lastNumber = -10000L

    @JvmOverloads
    fun update(
        snapshot: RadarTracker.Snapshot,
        now: Long,
        speakSpeed: Boolean,
        riderMps: Double = Double.NaN,
    ): String? {
        if (snapshot.health != RadarTracker.Health.LIVE) {
            announced.clear()
            lastNumber = -10000
            val lost = previousHealth == RadarTracker.Health.LIVE
            previousHealth = snapshot.health
            return if (lost) "Radar data lost" else null
        }
        previousHealth = snapshot.health
        if (speakSpeed) {
            if (snapshot.targets.isEmpty()) {
                lastNumber = -10000
                return null
            }
            if (!riderMps.isFinite() || riderMps < 0 || now - lastNumber < 1000) return null
            val selected =
                snapshot.targets
                    .filter { it.speedMps.isFinite() && it.speedMps >= 0 }
                    .minWithOrNull(
                        compareByDescending<RadarFrame.Target> { it.threat }
                            .thenBy { it.meters }
                            .thenBy { it.id }
                    ) ?: return null
            lastNumber = now
            return Math.round((selected.speedMps + riderMps) / .44704).toString()
        }
        lastNumber = -10000
        announced.keys.retainAll(snapshot.targets.map { it.id }.toSet())
        val candidate =
            snapshot.targets
                .filter { it.threat > 0 && it.threat > (announced[it.id] ?: -1) }
                .maxByOrNull { it.threat } ?: return null
        if (now - lastSpoken < if (candidate.threat == 2) 1500 else 4000) return null
        snapshot.targets.forEach { announced[it.id] = it.threat }
        lastSpoken = now
        return if (candidate.threat == 2) "Fast vehicle behind" else "Vehicle behind"
    }

    fun reset() {
        announced.clear()
        previousHealth = RadarTracker.Health.DISCONNECTED
        lastSpoken = -10000
        lastNumber = -10000
    }
}
