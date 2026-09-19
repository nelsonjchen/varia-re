package dev.nelson.ridewatch.core

/** State-change chimes. Silence or failure must never emit the empty-stream tone. */
class TonePolicy {
    enum class Tone {
        CONNECTED,
        APPROACH,
        URGENT,
        EMPTY,
        LOST,
    }

    private var live = false
    private var previousThreat = 0
    private var hadTargets = false

    fun update(snapshot: RadarTracker.Snapshot): Tone? {
        if (snapshot.health != RadarTracker.Health.LIVE) {
            val lost = live
            reset()
            return if (lost) Tone.LOST else null
        }
        val targets = snapshot.targets.isNotEmpty()
        val threat = snapshot.targets.maxOfOrNull { it.threat } ?: 0
        val result =
            when {
                targets && (!hadTargets || threat > previousThreat) ->
                    if (threat == 2) Tone.URGENT else Tone.APPROACH
                hadTargets && !targets -> Tone.EMPTY
                !live -> Tone.CONNECTED
                else -> null
            }
        live = true
        hadTargets = targets
        previousThreat = threat
        return result
    }

    fun reset() {
        live = false
        hadTargets = false
        previousThreat = 0
    }
}
