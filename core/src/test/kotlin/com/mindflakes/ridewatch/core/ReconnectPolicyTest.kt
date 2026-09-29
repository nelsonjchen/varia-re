package com.mindflakes.ridewatch.core

import org.junit.Assert.*
import org.junit.Test

class ReconnectPolicyTest {
    @Test
    fun absentRadarSwitchesToAutoConnect() {
        val policy = ReconnectPolicy()
        policy.start()
        assertFalse(policy.autoConnect)
        assertFalse(policy.keepWaiting(false, 8))
        assertEquals(1000L, policy.retryDelayMillis())
        assertTrue(policy.autoConnect)
        for (status in listOf(0, 8, 19)) assertTrue(policy.keepWaiting(false, status))
    }

    @Test
    fun transportLossAndStackErrorsRequireFreshClient() {
        val policy = ReconnectPolicy()
        policy.retryDelayMillis()
        assertFalse(policy.keepWaiting(true, 8))
        assertFalse(policy.keepWaiting(true, 19))
        assertFalse(policy.keepWaiting(false, 133))
        assertFalse(policy.keepWaiting(false, 257))
    }

    @Test
    fun repeatedFaultsBackOffAndCapWithoutOverflow() {
        val policy = ReconnectPolicy()
        assertEquals(
            listOf(1000L, 2000L, 4000L, 8000L, 16000L, 32000L, 64000L, 120000L),
            List(8) { policy.retryDelayMillis() },
        )
        repeat(100) { assertEquals(120000L, policy.retryDelayMillis()) }
    }

    @Test
    fun successfulSubscriptionResetsFaultBackoffButKeepsAutoMode() {
        val policy = ReconnectPolicy()
        repeat(8) { policy.retryDelayMillis() }
        policy.ready()
        assertTrue(policy.autoConnect)
        assertEquals(1000L, policy.retryDelayMillis())
    }

    @Test
    fun explicitOpeningRestoresImmediateDirectAttempt() {
        val policy = ReconnectPolicy()
        repeat(8) { policy.retryDelayMillis() }
        policy.start()
        assertFalse(policy.autoConnect)
        assertEquals(1000L, policy.retryDelayMillis())
    }
}
