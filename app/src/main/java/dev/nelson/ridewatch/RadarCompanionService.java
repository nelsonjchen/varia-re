package dev.nelson.ridewatch;

import android.companion.CompanionDeviceService;
import android.companion.DevicePresenceEvent;
import android.content.Intent;

public final class RadarCompanionService extends CompanionDeviceService {
  @Override
  public void onDevicePresenceEvent(DevicePresenceEvent event) {
    if (event.getEvent() != DevicePresenceEvent.EVENT_BLE_APPEARED
        && event.getEvent() != DevicePresenceEvent.EVENT_BT_CONNECTED) return;
    var prefs = getSharedPreferences("ride", 0);
    if (!RadarService.active
        && !"join".equals(prefs.getString("mode", "ride"))
        && prefs.getBoolean("enabled", false)
        && event.getAssociationId() >= 0
        && event.getAssociationId() == prefs.getInt("association", -1)) {
      try {
        startForegroundService(
            new Intent(this, RadarService.class)
                .setAction("presence")
                .putExtra("mode", prefs.getString("mode", "ride")));
      } catch (RuntimeException ignored) {
        BootReceiver.remind(this, "Radar nearby · tap to resume monitoring");
      }
    }
  }
}
