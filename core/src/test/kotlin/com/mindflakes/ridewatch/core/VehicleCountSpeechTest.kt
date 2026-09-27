package com.mindflakes.ridewatch.core

import org.junit.Assert.*
import org.junit.Test

class VehicleCountSpeechTest {
    private fun live(count: Int) =
        RadarTracker.Snapshot(
            RadarTracker.Health.LIVE,
            (1..count).map { RadarFrame.Target(it, 1, it * 20, 10.0) },
            -1,
        )

    @Test
    fun countChangesAreSpokenOnceAndNumbersKeepRepeating() {
        val policy = AlertPolicy()
        assertEquals("22", policy.update(live(1), 0, true, 0.0))
        assertEquals("2 cars. 22", policy.update(live(2), 1000, true, 0.0))
        assertEquals("22", policy.update(live(2), 2000, true, 0.0))
        assertEquals("3 cars. 22", policy.update(live(3), 3000, true, 0.0))
        assertEquals("1 car. 22", policy.update(live(1), 4000, true, 0.0))
        assertEquals("22", policy.update(live(1), 5000, true, 0.0))
        assertNull(policy.update(live(0), 5500, true, 0.0))
        assertEquals("22", policy.update(live(1), 5600, true, 0.0))
    }

    @Test
    fun rateLimitCoalescesToCurrentCountAndUnavailableSpeedDoesNotConsumeIt() {
        val policy = AlertPolicy()
        assertEquals("22", policy.update(live(1), 0, true, 0.0))
        assertNull(policy.update(live(2), 500, true, 0.0))
        assertNull(policy.update(live(3), 1000, true, Double.NaN))
        assertEquals("3 cars. 22", policy.update(live(3), 1500, true, 0.0))
    }

    @Test
    fun resetAndSpeechReenableReannounceGroup() {
        val policy = AlertPolicy()
        assertEquals("2 cars. 22", policy.update(live(2), 0, true, 0.0))
        policy.reset()
        assertEquals("2 cars. 22", policy.update(live(2), 100, true, 0.0))
        policy.update(live(2), 200, false, 0.0)
        assertEquals("2 cars. 22", policy.update(live(2), 300, true, 0.0))
    }
}
