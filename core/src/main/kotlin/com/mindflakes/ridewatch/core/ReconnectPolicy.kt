package com.mindflakes.ridewatch.core

/** Direct user-requested connection, then Android-managed waiting; back off stack faults. */
class ReconnectPolicy {
    var autoConnect = false
        private set

    private var failures = 0

    fun start() {
        autoConnect = false
        failures = 0
    }

    fun ready() {
        failures = 0
    }

    fun retryDelayMillis(): Long {
        autoConnect = true
        return minOf(120_000L, 1_000L shl minOf(failures++, 7))
    }

    // A pending auto-connect can report absence without invalidating its registration.
    // An established connection gets a fresh client on loss, rejecting old callbacks.
    fun keepWaiting(wasConnected: Boolean, status: Int): Boolean =
        autoConnect && !wasConnected && status in setOf(0, 8, 19)
}
