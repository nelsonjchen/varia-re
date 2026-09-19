package dev.nelson.ridewatch;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.location.*;
import android.media.AudioAttributes;
import android.os.*;
import android.speech.tts.TextToSpeech;
import dev.nelson.ridewatch.core.*;
import java.util.*;

public final class RadarService extends Service {
  static volatile boolean active;
  static final String UPDATE = "dev.nelson.ridewatch.UPDATE";
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final RadarTracker tracker = new RadarTracker();
  private final LegacyDecoder decoder = new LegacyDecoder();
  private final AlertPolicy alerts = new AlertPolicy();
  private final MotionGate motion = new MotionGate();
  private BleRadar ble;
  private TextToSpeech tts;
  private boolean speechReady, armed, riding;
  private int battery = -1;
  private String detail = "Ready", mode = "ride";
  private LocationManager locations;
  private GroupLink group;
  private int groupGeneration;
  private RadarTracker.Snapshot remote;
  private long remoteAt = -1;
  private final LocationListener locationListener =
      new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
          long now = SystemClock.elapsedRealtime();
          if (armed
              && location.hasSpeed()
              && location.hasSpeedAccuracy()
              && motion.update(
                  location.getSpeed(),
                  location.getSpeedAccuracyMetersPerSecond(),
                  location.getElapsedRealtimeNanos() / 1_000_000,
                  now)) {
            armed = false;
            mode = "ride";
            getSharedPreferences("ride", 0).edit().putString("mode", mode).apply();
            detail = "Movement detected · connecting";
            beginRadar();
          }
        }

        @Override
        public void onProviderDisabled(String provider) {
          if (armed) detail = "Location is off · waiting for movement";
        }

        @Override
        public void onProviderEnabled(String provider) {}

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}
      };

  @Override
  public void onCreate() {
    super.onCreate();
    active = true;
    createChannel(this);
    tts =
        new TextToSpeech(
            this,
            result -> {
              speechReady = result == TextToSpeech.SUCCESS;
              if (speechReady) {
                tts.setLanguage(Locale.US);
                tts.setAudioAttributes(
                    new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
              }
            });
    ble =
        new BleRadar(
            this,
            new BleRadar.Listener() {
              public void status(String text) {
                detail = text;
              }

              public void connected() {
                tracker.connected();
              }

              public void disconnected() {
                tracker.reset();
                battery = -1;
              }

              public void battery(int value) {
                battery = value;
              }

              public boolean packet(byte[] bytes) {
                try {
                  RadarFrame frame = decoder.decode(bytes);
                  tracker.accept(frame, SystemClock.elapsedRealtime());
                  if (frame.kind == RadarFrame.Kind.MEASUREMENT)
                    detail = "Connected · receiving legacy radar measurements";
                  if (frame.kind == RadarFrame.Kind.UNSUPPORTED)
                    detail = "Unsupported radar packet · awaiting measurements";
                  return frame.kind != RadarFrame.Kind.UNSUPPORTED;
                } catch (IllegalArgumentException error) {
                  detail = "Malformed radar packet ignored";
                  return false;
                }
              }
            });
  }

  static void createChannel(Context context) {
    context
        .getSystemService(NotificationManager.class)
        .createNotificationChannel(
            new NotificationChannel(
                "ride", "Radar monitoring", NotificationManager.IMPORTANCE_LOW));
  }

  private Notification notification(String text) {
    PendingIntent open =
        PendingIntent.getActivity(
            this,
            0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    PendingIntent stop =
        PendingIntent.getService(
            this,
            1,
            new Intent(this, RadarService.class).setAction("stop"),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    return new Notification.Builder(this, "ride")
        .setSmallIcon(dev.nelson.ridewatch.R.drawable.ic_radar)
        .setContentTitle("RideWatch")
        .setContentText(text)
        .setContentIntent(open)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
        .build();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    String action = intent == null ? "resume" : intent.getAction();
    if ("stop".equals(action)) {
      getSharedPreferences("ride", 0).edit().putBoolean("enabled", false).apply();
      stopSelf();
      return START_NOT_STICKY;
    }
    if ("resume".equals(action) && !getSharedPreferences("ride", 0).getBoolean("enabled", false)) {
      stopSelf();
      return START_NOT_STICKY;
    }
    mode =
        intent == null
            ? getSharedPreferences("ride", 0).getString("mode", "ride")
            : intent.getStringExtra("mode");
    if (mode == null) mode = "ride";
    // Presence wake reconnects BLE without attempting a background location FGS.
    armed = "motion".equals(mode) && !"presence".equals(action);
    if ("presence".equals(action) && "motion".equals(mode)) mode = "ride";
    try {
      if (Build.VERSION.SDK_INT >= 29) {
        int types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
        if (armed) types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
        startForeground(
            1, notification(armed ? "Armed · waiting for movement" : "Connecting radar…"), types);
      } else startForeground(1, notification("Starting radar…"));
    } catch (RuntimeException error) {
      detail = "Could not start monitoring: " + error.getClass().getSimpleName();
      broadcast(tracker.snapshot(SystemClock.elapsedRealtime()));
      stopSelf();
      return START_NOT_STICKY;
    }
    getSharedPreferences("ride", 0)
        .edit()
        .putBoolean("enabled", true)
        .putString("mode", mode)
        .apply();
    handler.removeCallbacks(tick);
    ble.stop();
    riding = false;
    alerts.reset();
    motion.reset();
    stopLocation();
    if (group != null) {
      group.close();
      group = null;
    }
    final int generation = ++groupGeneration;
    remote = null;
    remoteAt = -1;
    if ("join".equals(mode) || "host".equals(mode)) {
      try {
        var prefs = getSharedPreferences("ride", 0);
        group =
            new GroupLink(
                "host".equals(mode),
                prefs.getString("host", ""),
                prefs.getString("key", ""),
                new GroupLink.Listener() {
                  public void snapshot(RadarTracker.Snapshot value) {
                    handler.post(
                        () -> {
                          if (generation != groupGeneration) return;
                          remote = value;
                          remoteAt = SystemClock.elapsedRealtime();
                          detail = "Receiving host radar · distances relative to host";
                        });
                  }

                  public void status(String text) {
                    handler.post(
                        () -> {
                          if (generation == groupGeneration) detail = text;
                        });
                  }
                });
        group.start();
      } catch (Exception error) {
        detail = "Group setup failed: " + error.getMessage();
      }
    }
    if ("join".equals(mode)) {
      if (group != null) detail = "Joined mode · waiting for host";
    } else if (armed) startLocation();
    else beginRadar();
    handler.post(tick);
    return START_STICKY;
  }

  @SuppressLint("MissingPermission")
  private void startLocation() {
    detail = "Armed · sustained movement above 4 mph starts radar";
    locations = getSystemService(LocationManager.class);
    if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) {
      detail = "Precise location permission needed for motion mode";
      return;
    }
    try {
      locations.requestLocationUpdates(
          LocationManager.GPS_PROVIDER, 2000, 0, locationListener, Looper.getMainLooper());
    } catch (RuntimeException error) {
      detail = "Location unavailable · open app to retry";
    }
  }

  private void stopLocation() {
    if (locations != null) {
      locations.removeUpdates(locationListener);
      locations = null;
    }
  }

  private void beginRadar() {
    if (riding) return;
    riding = true;
    stopLocation();
    ble.start(getSharedPreferences("ride", 0).getString("radar", null));
  }

  private final Runnable tick =
      new Runnable() {
        public void run() {
          long now = SystemClock.elapsedRealtime();
          RadarTracker.Snapshot snapshot = tracker.snapshot(now);
          if ("join".equals(mode))
            snapshot =
                remote != null && now - remoteAt < 3000
                    ? remote
                    : new RadarTracker.Snapshot(RadarTracker.Health.STALE, List.of(), -1);
          if (group != null && "host".equals(mode)) group.publish(snapshot);
          String speech =
              alerts.update(
                  snapshot, now, getSharedPreferences("ride", 0).getBoolean("speed", false));
          if (speech != null
              && speechReady
              && getSharedPreferences("ride", 0).getBoolean("speech", true))
            tts.speak(
                ("join".equals(mode) ? "Group radar. " : "") + speech,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "radar");
          broadcast(snapshot);
          getSystemService(NotificationManager.class)
              .notify(1, notification(armed ? detail : headline(snapshot)));
          handler.postDelayed(this, 500);
        }
      };

  static String headline(RadarTracker.Snapshot snapshot) {
    return switch (snapshot.health()) {
      case DISCONNECTED -> "Radar disconnected";
      case WAITING -> "Waiting for radar data";
      case STALE -> "Radar data lost";
      case ERROR -> "Radar error " + snapshot.error();
      case LIVE ->
          snapshot.targets().isEmpty()
              ? "No targets detected"
              : snapshot.targets().size()
                  + " vehicle"
                  + (snapshot.targets().size() == 1 ? "" : "s")
                  + " behind";
    };
  }

  private void broadcast(RadarTracker.Snapshot snapshot) {
    StringBuilder targets = new StringBuilder();
    for (var target : snapshot.targets())
      targets
          .append(target.meters())
          .append(" m    ·    ")
          .append(target.threat() == 2 ? "Fast approach" : "Detected")
          .append("    ·    ")
          .append(Math.round(target.speedMps() / .44704))
          .append(" mph radar speed\n");
    int threat = snapshot.targets().stream().mapToInt(RadarFrame.Target::threat).max().orElse(0);
    sendBroadcast(
        new Intent(UPDATE)
            .setPackage(getPackageName())
            .putExtra("title", armed ? "Ready for movement" : headline(snapshot))
            .putExtra("detail", detail)
            .putExtra("targets", targets.toString())
            .putExtra("health", snapshot.health().name())
            .putExtra("threat", threat)
            .putExtra("battery", battery)
            .putExtra("group", "join".equals(mode)));
  }

  @Override
  public void onDestroy() {
    active = false;
    groupGeneration++;
    handler.removeCallbacksAndMessages(null);
    if (ble != null) ble.stop();
    stopLocation();
    if (group != null) group.close();
    if (tts != null) {
      tts.stop();
      tts.shutdown();
    }
    tracker.reset();
    detail = "Monitoring stopped";
    armed = false;
    broadcast(tracker.snapshot(SystemClock.elapsedRealtime()));
    stopForeground(STOP_FOREGROUND_REMOVE);
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
