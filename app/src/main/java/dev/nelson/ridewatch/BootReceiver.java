package dev.nelson.ridewatch;

import android.app.*;
import android.content.*;

public final class BootReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
        && context.getSharedPreferences("ride", 0).getBoolean("enabled", false))
      remind(context, "Phone restarted · tap to resume or arm radar");
  }

  static void remind(Context context, String text) {
    RadarService.createChannel(context);
    PendingIntent open =
        PendingIntent.getActivity(
            context, 2, new Intent(context, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
    try {
      context
          .getSystemService(NotificationManager.class)
          .notify(
              2,
              new Notification.Builder(context, "ride")
                  .setSmallIcon(R.drawable.ic_radar)
                  .setContentTitle("RideWatch")
                  .setContentText(text)
                  .setContentIntent(open)
                  .setAutoCancel(true)
                  .build());
    } catch (SecurityException ignored) {
    }
  }
}
