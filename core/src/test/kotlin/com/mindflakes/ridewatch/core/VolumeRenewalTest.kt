package com.mindflakes.ridewatch.core

import org.junit.Assert.*
import org.junit.Test

class VolumeRenewalTest {
    @Test
    fun reopeningRaisesToMaximumAndPreservesOriginalBaseline() {
        val next = RideVolumePolicy(3, 12).renewed(12, 15)
        assertEquals(15, next.raised)
        assertEquals(3, next.restore(15))
        assertEquals(3, next.renewed(15, 15).restore(15))
    }

    @Test
    fun userAdjustmentBecomesBaselineOnNextExplicitRaise() {
        val next = RideVolumePolicy(3, 15, true).renewed(6, 15)
        assertEquals(6, next.original)
        assertEquals(15, next.raised)
        assertEquals(6, next.restore(15))
        assertNull(next.restore(8))
    }

    @Test
    fun detectsAdjustmentEvenBeforeObserverRuns() {
        assertEquals(5, RideVolumePolicy(3, 15).renewed(5, 15).original)
    }

    @Test
    fun disabledOrLowerMinimumDoesNotLowerCurrentVolume() {
        val next = RideVolumePolicy(3, 15).renewed(15, 0)
        assertEquals(15, next.raised)
        assertEquals(3, next.restore(15))
        assertEquals(15, RideVolumePolicy.minimum(15, 100))
        assertEquals(25, RideVolumePolicy.minimum(25, 100))
    }
}
