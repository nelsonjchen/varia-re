package dev.nelson.ridewatch.core

import org.junit.Assert.*
import org.junit.Test

class DisconnectPolicyTest {
    @Test
    fun disconnectBeforeFirstMeasurementStillAnnounces() {
        val policy = DisconnectPolicy()
        assertNull(policy.disconnected()) // Startup/failed attempts are not a lost connection.
        policy.connected()
        assertEquals("Radar disconnected", policy.disconnected())
        assertNull(policy.disconnected())
    }

    @Test
    fun lastBatterySurvivesLossAndFailedReconnectAttempts() {
        val policy = DisconnectPolicy()
        policy.connected()
        policy.battery(84)
        assertEquals("Disconnected. Last battery 84 percent", policy.disconnected())
        repeat(5) { assertNull(policy.disconnected()) }
        assertEquals(84, policy.lastBattery)
        policy.connected() // Reconnect loses link before a fresh battery read.
        assertEquals("Disconnected. Last battery 84 percent", policy.disconnected())
        policy.connected()
        policy.battery(79)
        assertEquals("Disconnected. Last battery 79 percent", policy.disconnected())
    }

    @Test
    fun staleWarningDoesNotConsumeActualDisconnectAnnouncement() {
        val tones = TonePolicy()
        val policy = DisconnectPolicy()
        policy.connected()
        policy.battery(0)
        assertEquals(
            TonePolicy.Tone.CONNECTED,
            tones.update(RadarTracker.Snapshot(RadarTracker.Health.LIVE, emptyList(), -1)),
        )
        assertEquals(
            TonePolicy.Tone.LOST,
            tones.update(RadarTracker.Snapshot(RadarTracker.Health.STALE, emptyList(), -1)),
        )
        assertEquals("Disconnected. Last battery 0 percent", policy.disconnected())
    }

    @Test
    fun intentionalResetIsSilentAndDoesNotReuseAnotherRadarsBattery() {
        val policy = DisconnectPolicy()
        policy.connected()
        policy.battery(45)
        policy.battery(101)
        policy.battery(-1)
        assertEquals(45, policy.lastBattery)
        policy.reset()
        assertNull(policy.disconnected())
        policy.connected()
        assertEquals("Radar disconnected", policy.disconnected())
    }
}
