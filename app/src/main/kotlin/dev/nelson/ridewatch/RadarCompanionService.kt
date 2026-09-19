package dev.nelson.ridewatch

import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.content.Intent

class RadarCompanionService : CompanionDeviceService() {
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        if (RadarService.TESTING_LIFECYCLE) return
        if (
            event.event != DevicePresenceEvent.EVENT_BLE_APPEARED &&
                event.event != DevicePresenceEvent.EVENT_BT_CONNECTED
        )
            return
        val prefs = getSharedPreferences("ride", 0)
        if (
            !RadarService.active &&
                prefs.getString("mode", "ride") != "join" &&
                prefs.getBoolean("enabled", false) &&
                event.associationId >= 0 &&
                event.associationId == prefs.getInt("association", -1)
        ) {
            try {
                startForegroundService(
                    Intent(this, RadarService::class.java)
                        .setAction("presence")
                        .putExtra("mode", prefs.getString("mode", "ride"))
                )
            } catch (_: RuntimeException) {
                BootReceiver.remind(this, "Radar nearby · tap to resume monitoring")
            }
        }
    }
}
