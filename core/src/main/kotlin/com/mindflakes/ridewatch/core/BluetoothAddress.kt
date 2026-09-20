package com.mindflakes.ridewatch.core

import java.util.Locale

/** CDM returns lowercase addresses; BluetoothAdapter requires uppercase hex. */
object BluetoothAddress {
    @JvmStatic
    fun canonicalize(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val address = value.trim().uppercase(Locale.ROOT)
        require(Regex("[0-9A-F]{2}(:[0-9A-F]{2}){5}").matches(address)) {
            "Invalid saved radar address; pair the radar again"
        }
        return address
    }
}
