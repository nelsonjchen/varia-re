package com.mindflakes.ridewatch.core

import org.junit.Assert.*
import org.junit.Test

class RideVolumePolicyTest {
    @Test
    fun minimumRoundsUpToAnAvailableStep() {
        assertEquals(6, RideVolumePolicy.minimum(7, 80))
        assertEquals(0, RideVolumePolicy.minimum(7, 0))
        assertEquals(7, RideVolumePolicy.minimum(7, 100))
    }

    @Test
    fun raisedVolumeRestoresIncludingInitiallyMuted() {
        assertEquals(0, RideVolumePolicy(0, 6).restore(6))
        assertEquals(3, RideVolumePolicy(3, 6).restore(6))
        assertNull(RideVolumePolicy(7, 7).restore(7))
    }

    @Test
    fun ManualAdjustmentWinsEvenWhenItReturnsToRaisedLevel() {
        val policy = RideVolumePolicy(2, 6)
        policy.observe(4)
        policy.observe(6)
        assertNull(policy.restore(6))
        assertNull(RideVolumePolicy(2, 6).restore(0))
    }

    @Test
    fun RecoveredManualOverrideIsRetained() {
        assertNull(RideVolumePolicy(2, 6, true).restore(6))
    }
}
