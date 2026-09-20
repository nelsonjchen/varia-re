package com.mindflakes.ridewatch.core

/** Monotonic-clock freshness, independent of Android, transport and UI. */
class RadarTracker {
    enum class Health {
        DISCONNECTED,
        WAITING,
        LIVE,
        STALE,
        ERROR,
    }

    @JvmRecord
    data class Snapshot(val health: Health, val targets: List<RadarFrame.Target>, val error: Int)

    private data class Seen(val target: RadarFrame.Target, val at: Long)

    private val tracks = mutableMapOf<Int, Seen>()
    private var connected = false
    private var lastFrame = -1L
    private var error = -1

    fun connected() {
        reset()
        connected = true
    }

    fun reset() {
        connected = false
        lastFrame = -1
        error = -1
        tracks.clear()
    }

    fun accept(frame: RadarFrame, now: Long) {
        if (!connected || frame.kind == RadarFrame.Kind.UNSUPPORTED) return
        if (frame.kind == RadarFrame.Kind.ERROR) {
            error = frame.error
            tracks.clear()
            return
        }
        error = -1
        lastFrame = now
        for (target in frame.targets) tracks[target.id] = Seen(target, now)
    }

    fun snapshot(now: Long): Snapshot {
        if (!connected) return Snapshot(Health.DISCONNECTED, emptyList(), -1)
        if (error >= 0) return Snapshot(Health.ERROR, emptyList(), error)
        if (lastFrame < 0) return Snapshot(Health.WAITING, emptyList(), -1)
        if (now - lastFrame >= 2000) {
            tracks.clear()
            return Snapshot(Health.STALE, emptyList(), -1)
        }
        tracks.values.removeIf { now - it.at >= if (it.target.meters < 5) 500 else 2000 }
        return Snapshot(Health.LIVE, tracks.values.map { it.target }.sortedBy { it.meters }, -1)
    }
}
