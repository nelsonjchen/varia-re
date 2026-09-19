package dev.nelson.ridewatch;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import com.google.android.gms.location.*;
import dev.nelson.ridewatch.core.RiderSpeed;

/** Foreground ride-session speed. Does not record positions or wake a paused ride. */
final class RideSpeedLocation {
  private final FusedLocationProviderClient client;
  private final RiderSpeed speed = new RiderSpeed();
  private LocationCallback callback;
  private boolean requested;
  private String message = "Precise location needed · pause/resume to enable speed";

  RideSpeedLocation(Context context) {
    client = LocationServices.getFusedLocationProviderClient(context);
  }

  void setEnabled(boolean enabled) {
    if (enabled && !requested) start();
    else if (!enabled && requested) stop();
  }

  @SuppressLint("MissingPermission")
  private void start() {
    stop();
    requested = true;
    message = "Waiting for accurate rider speed";
    LocationCallback current =
        new LocationCallback() {
          @Override
          public void onLocationResult(LocationResult result) {
            if (callback != this) return;
            for (var location : result.getLocations()) {
              speed.accept(
                  location.hasSpeed() ? location.getSpeed() : Double.NaN,
                  location.hasSpeedAccuracy()
                      ? location.getSpeedAccuracyMetersPerSecond()
                      : Double.NaN,
                  location.getElapsedRealtimeNanos() / 1_000_000);
            }
          }
        };
    callback = current;
    try {
      client
          .requestLocationUpdates(
              new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                  .setMinUpdateIntervalMillis(1000)
                  .setMaxUpdateDelayMillis(0)
                  .setMaxUpdateAgeMillis(0)
                  .setWaitForAccurateLocation(true)
                  .build(),
              current,
              Looper.getMainLooper())
          .addOnFailureListener(
              error -> {
                if (callback != current) return;
                stop();
                requested = true;
                message = "Rider speed unavailable · pause/resume to retry";
              });
    } catch (RuntimeException error) {
      stop();
      requested = true;
      message = "Rider speed unavailable · pause/resume to retry";
    }
  }

  double at(long now) {
    return speed.estimateAt(now);
  }

  String description() {
    double value = speed.at(SystemClock.elapsedRealtime());
    return Double.isFinite(value)
        ? "Estimated car speed · rider " + Math.round(value / .44704) + " mph"
        : "Rider speed assumed 0 · " + message;
  }

  void stop() {
    requested = false;
    var previous = callback;
    callback = null;
    speed.clear();
    if (previous != null) {
      try {
        client.removeLocationUpdates(previous);
      } catch (RuntimeException ignored) {
      }
    }
  }
}
