package dev.nelson.ridewatch

import android.app.*
import android.content.*

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (RadarService.TESTING_LIFECYCLE) return
        if (
            intent.action == Intent.ACTION_BOOT_COMPLETED &&
                context.getSharedPreferences("ride", 0).getBoolean("enabled", false)
        )
            remind(context, "Phone restarted · tap to resume or arm radar")
    }

    companion object {
        fun remind(context: Context, text: String) {
            RadarService.createChannel(context)
            val open =
                PendingIntent.getActivity(
                    context,
                    2,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            try {
                context
                    .getSystemService(NotificationManager::class.java)
                    .notify(
                        2,
                        Notification.Builder(context, "ride")
                            .setSmallIcon(R.drawable.ic_radar)
                            .setContentTitle("RideWatch")
                            .setContentText(text)
                            .setContentIntent(open)
                            .setAutoCancel(true)
                            .build(),
                    )
            } catch (_: SecurityException) {}
        }
    }
}
