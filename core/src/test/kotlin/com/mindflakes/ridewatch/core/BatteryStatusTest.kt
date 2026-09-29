package com.mindflakes.ridewatch.core

import org.junit.Assert.*
import org.junit.Test

class BatteryStatusTest {
    @Test
    fun notificationAndIndicationSelectionMatchesGarmin() {
        assertNull(BatteryStatus.cccdValue(2))
        assertEquals(1, BatteryStatus.cccdValue(0x12))
        assertEquals(2, BatteryStatus.cccdValue(0x22))
        assertEquals(2, BatteryStatus.cccdValue(0x32))
    }

    @Test
    fun unchangedValidReportRefreshesAgeButInvalidReportDoesNot() {
        val battery = BatteryStatus()
        assertEquals(-1L, battery.ageMillis(1000))
        battery.accept(84, 1000)
        assertEquals(60_000L, battery.ageMillis(61_000))
        battery.accept(84, 61_000)
        assertEquals(0L, battery.ageMillis(61_000))
        battery.accept(255, 62_000)
        assertEquals(1000L, battery.ageMillis(62_000))
        assertEquals(84, battery.percent)
    }

    @Test
    fun retainsActualLevelsIncludingZeroAndResetsBetweenSessions() {
        val battery = BatteryStatus()
        for (level in listOf(100, 84, 63, 1, 0)) {
            battery.accept(level, 1000)
            assertEquals(level, battery.percent)
        }
        battery.reset()
        assertEquals(-1, battery.percent)
        assertEquals(-1L, battery.ageMillis(1000))
    }
}
