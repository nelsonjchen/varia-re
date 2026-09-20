package com.mindflakes.ridewatch.core

/** Never lower an already louder stream or undo a user's observed adjustment. */
class RideVolumePolicy(val original: Int, val raised: Int, var overridden: Boolean = false) {
    fun observe(current: Int) {
        if (current != raised) overridden = true
    }

    fun restore(current: Int): Int? {
        observe(current)
        return if (!overridden && raised > original) original else null
    }

    companion object {
        fun minimum(maximum: Int, percent: Int): Int =
            (maximum * percent.coerceIn(0, 100) + 99) / 100
    }
}
