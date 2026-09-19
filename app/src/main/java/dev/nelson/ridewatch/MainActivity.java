package dev.nelson.ridewatch;

import android.Manifest;
import android.app.*;
import android.bluetooth.le.*;
import android.companion.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import dev.nelson.ridewatch.core.*;
import java.util.*;

public final class MainActivity extends Activity {
  private TextView headline, detail, battery;
  private RadarView radar;
  private RadarAudio testAudio;
  private Button rideButton;
  private LinearLayout content;
  private String pendingMode;
  private boolean demo;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private SharedPreferences prefs;
  private static final int LIME = Color.rgb(210, 239, 108),
      INK = Color.rgb(16, 23, 19),
      MUTED = Color.rgb(164, 180, 170);

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    prefs = getSharedPreferences("ride", 0);
    content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setBackgroundColor(Color.rgb(9, 13, 16));
    setContentView(content);
    content.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          var safe =
              insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
          content.setPadding(
              safe.left + dp(16), safe.top + dp(8), safe.right + dp(16), safe.bottom + dp(8));
          return insets;
        });
    text("RIDEWATCH", 16, Color.WHITE).setLetterSpacing(.16f);
    battery = text("RVR315  ·  OFFLINE", 12, MUTED);
    battery.setPadding(0, dp(4), 0, dp(10));
    headline = text("Radar is off", 24, MUTED);
    headline.setPadding(dp(12), dp(10), dp(12), dp(10));
    headline.setBackgroundColor(Color.rgb(27, 35, 40));
    radar = new RadarView(this);
    content.addView(radar, new LinearLayout.LayoutParams(-1, 0, 1));
    detail = text("Pair your radar in Controls to get started.", 13, MUTED);
    detail.setMaxLines(3);
    detail.setPadding(0, dp(6), 0, dp(6));
    LinearLayout actions = new LinearLayout(this);
    rideButton = new Button(this);
    rideButton.setText(prefs.getBoolean("enabled", false) ? "Stop ride" : "Start ride");
    rideButton.setAllCaps(false);
    rideButton.setMinHeight(dp(52));
    rideButton.setOnClickListener(
        v -> {
          if (demo || prefs.getBoolean("enabled", false)) stopRide();
          else start("ride");
        });
    actions.addView(rideButton, new LinearLayout.LayoutParams(0, -2, 1));
    Button settings = new Button(this);
    settings.setText("Controls");
    settings.setAllCaps(false);
    settings.setMinHeight(dp(52));
    settings.setOnClickListener(v -> controls());
    actions.addView(settings, new LinearLayout.LayoutParams(0, -2, 1));
    content.addView(actions);
    if (getResources().getConfiguration().orientation
        == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
      content.removeView(radar);
      content.addView(new View(this), 3, new LinearLayout.LayoutParams(-1, 0, 1));
      content.setOnApplyWindowInsetsListener(null);
      content.setPadding(0, 0, dp(24), 0);
      LinearLayout wide = new LinearLayout(this);
      wide.setBackgroundColor(Color.rgb(9, 13, 16));
      ((ViewGroup) content.getParent()).removeView(content);
      wide.addView(content, new LinearLayout.LayoutParams(0, -1, 1));
      wide.addView(radar, new LinearLayout.LayoutParams(0, -1, 1));
      setContentView(wide);
      wide.setOnApplyWindowInsetsListener(
          (view, insets) -> {
            var safe =
                insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            wide.setPadding(
                safe.left + dp(16), safe.top + dp(8), safe.right + dp(16), safe.bottom + dp(8));
            return insets;
          });
    }
  }

  private void stopRide() {
    demo = false;
    handler.removeCallbacksAndMessages(null);
    prefs.edit().putBoolean("enabled", false).apply();
    stopService(new Intent(this, RadarService.class));
    render("Radar is off", "Monitoring stopped", "DISCONNECTED", 0, new int[0], new int[0], false);
    rideButton.setText("Start ride");
    battery.setText("RVR315  ·  OFFLINE");
  }

  private void controls() {
    new AlertDialog.Builder(this)
        .setTitle("Ride controls")
        .setItems(
            new String[] {
              "Pair radar / nearby reconnect",
              "Arm motion · above 4 mph",
              "Sound & speech",
              "Host a group",
              "Join a group",
              "Preview radar display"
            },
            (dialog, which) -> {
              switch (which) {
                case 0 -> pair();
                case 1 -> start("motion");
                case 2 -> speechSettings();
                case 3 -> hostGroup();
                case 4 -> joinGroup();
                case 5 -> preview();
              }
            })
        .setNegativeButton("Close", null)
        .show();
  }

  private void speechSettings() {
    new AlertDialog.Builder(this)
        .setTitle("Sound & speech")
        .setMultiChoiceItems(
            new String[] {
              "Varia-style tones", "Spoken vehicle alerts", "Speak radar speed · experimental"
            },
            new boolean[] {
              prefs.getBoolean("tones", true),
              prefs.getBoolean("speech", true),
              prefs.getBoolean("speed", false)
            },
            (dialog, which, checked) ->
                prefs
                    .edit()
                    .putBoolean(new String[] {"tones", "speech", "speed"}[which], checked)
                    .apply())
        .setNeutralButton("Test tones", (dialog, which) -> testTones())
        .setPositiveButton("Done", null)
        .show();
  }

  private void testTones() {
    if (prefs.getBoolean("enabled", false)) {
      detail.setText("Stop monitoring before testing sounds.");
      return;
    }
    if (testAudio == null) testAudio = new RadarAudio(this);
    new AlertDialog.Builder(this)
        .setTitle("Test tones · media volume")
        .setItems(
            new String[] {
              "Connected",
              "Vehicle approaching",
              "Fast approach",
              "No vehicles detected",
              "Radar data lost"
            },
            (dialog, which) -> {
              var choices =
                  new TonePolicy.Tone[] {
                    TonePolicy.Tone.CONNECTED,
                    TonePolicy.Tone.APPROACH,
                    TonePolicy.Tone.URGENT,
                    TonePolicy.Tone.EMPTY,
                    TonePolicy.Tone.LOST
                  };
              testAudio.play(choices[which], null);
            })
        .setNegativeButton("Close", null)
        .show();
  }

  private void render(
      String title,
      String message,
      String health,
      int threat,
      int[] meters,
      int[] threats,
      boolean group) {
    int color =
        !"LIVE".equals(health)
            ? MUTED
            : threat == 2
                ? Color.rgb(255, 104, 94)
                : meters.length > 0 ? Color.rgb(255, 196, 85) : LIME;
    headline.setText(title);
    headline.setTextColor("LIVE".equals(health) ? INK : color);
    headline.setBackgroundColor("LIVE".equals(health) ? color : Color.rgb(27, 35, 40));
    detail.setText(message);
    radar.update(health, meters, threats, color, group, demo);
    radar.setKeepScreenOn(demo || "LIVE".equals(health));
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private TextView text(String value, int size, int color) {
    TextView view = new TextView(this);
    view.setText(value);
    view.setTextSize(size);
    view.setTextColor(color);
    content.addView(view);
    return view;
  }

  private final BroadcastReceiver receiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (demo) return;
          boolean group = intent.getBooleanExtra("group", false);
          int[] meters = intent.getIntArrayExtra("meters");
          int[] threats = intent.getIntArrayExtra("threats");
          render(
              intent.getStringExtra("title"),
              intent.getStringExtra("detail"),
              intent.getStringExtra("health"),
              intent.getIntExtra("threat", 0),
              meters == null ? new int[0] : meters,
              threats == null ? new int[0] : threats,
              group);
          int level = intent.getIntExtra("battery", -1);
          battery.setText(
              (group ? "GROUP · HOST RADAR" : "RVR315")
                  + (level < 0 ? "" : "  ·  " + level + "% at connection"));
          rideButton.setText(prefs.getBoolean("enabled", false) ? "Stop ride" : "Start ride");
        }
      };

  @Override
  public void onStart() {
    super.onStart();
    registerReceiver(
        receiver, new IntentFilter(RadarService.UPDATE), Context.RECEIVER_NOT_EXPORTED);
  }

  @Override
  public void onStop() {
    if (testAudio != null) testAudio.stop();
    unregisterReceiver(receiver);
    super.onStop();
  }

  private void start(String mode) {
    if (testAudio != null) testAudio.stop();
    demo = false;
    handler.removeCallbacksAndMessages(null);
    render(
        "Starting radar",
        "Waiting for connection",
        "WAITING",
        0,
        new int[0],
        new int[0],
        "join".equals(mode));
    battery.setText("RVR315 · CONNECTING");
    pendingMode = mode;
    ArrayList<String> needed = new ArrayList<>();
    needed.add(Manifest.permission.BLUETOOTH_SCAN);
    needed.add(Manifest.permission.BLUETOOTH_CONNECT);
    if ("motion".equals(mode)) {
      needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
      needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);
    }
    needed.add(Manifest.permission.POST_NOTIFICATIONS);
    needed.removeIf(
        permission -> checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED);
    if (!needed.isEmpty()) {
      requestPermissions(needed.toArray(new String[0]), 20);
      return;
    }
    launch(mode);
  }

  private void launch(String mode) {
    try {
      startForegroundService(
          new Intent(this, RadarService.class).setAction("start").putExtra("mode", mode));
    } catch (RuntimeException error) {
      detail.setText("Could not start: " + error.getMessage());
    }
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(code, permissions, results);
    if (code == 20) {
      for (int i = 0; i < permissions.length; i++)
        if (results[i] != PackageManager.PERMISSION_GRANTED
            && !Manifest.permission.POST_NOTIFICATIONS.equals(permissions[i])) {
          detail.setText("Required permission denied. Choose a mode again after granting it.");
          return;
        }
      launch(pendingMode);
    } else if (code == 21) {
      if (results.length == 0) return;
      for (int result : results) {
        if (result != PackageManager.PERMISSION_GRANTED) {
          detail.setText("Nearby devices permission is needed to pair and reconnect.");
          return;
        }
      }
      pair();
    }
  }

  private void pair() {
    if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        || checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(
          new String[] {Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
          21);
      return;
    }
    CompanionDeviceManager manager = getSystemService(CompanionDeviceManager.class);
    BluetoothLeDeviceFilter filter =
        new BluetoothLeDeviceFilter.Builder()
            .setScanFilter(
                new ScanFilter.Builder().setServiceUuid(new ParcelUuid(BleRadar.SERVICE)).build())
            .build();
    AssociationRequest request =
        new AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(false).build();
    CompanionDeviceManager.Callback callback =
        new CompanionDeviceManager.Callback() {
          @Override
          public void onAssociationPending(IntentSender chooser) {
            showChooser(chooser);
          }

          @Override
          public void onAssociationCreated(AssociationInfo association) {
            if (association.getDeviceMacAddress() != null) saveRadar(association);
          }

          @Override
          public void onFailure(CharSequence reason) {
            detail.setText("Pairing: " + reason);
          }
        };
    try {
      manager.associate(request, getMainExecutor(), callback);
    } catch (RuntimeException error) {
      detail.setText("Pairing unavailable: " + error.getMessage());
    }
  }

  private void showChooser(IntentSender chooser) {
    try {
      startIntentSenderForResult(chooser, 10, null, 0, 0, 0);
    } catch (IntentSender.SendIntentException error) {
      detail.setText("Pairing chooser unavailable");
    }
  }

  private void saveRadar(AssociationInfo association) {
    prefs
        .edit()
        .putString("radar", association.getDeviceMacAddress().toString())
        .putInt("association", association.getId())
        .apply();
    try {
      getSystemService(CompanionDeviceManager.class)
          .startObservingDevicePresence(
              new ObservingDevicePresenceRequest.Builder()
                  .setAssociationId(association.getId())
                  .build());
    } catch (RuntimeException error) {
      detail.setText("Radar saved; presence wake unavailable. Try pairing again.");
      return;
    }
    detail.setText("Radar saved. Start monitoring once to enable nearby reconnect.");
  }

  private void hostGroup() {
    String key = GroupCodec.newKey();
    new AlertDialog.Builder(this)
        .setTitle("Host a ride group")
        .setMessage(
            "Use the same Wi-Fi or hotspot. Peers need this phone’s local IP address from network"
                + " settings and this join code:\n\n"
                + key
                + "\n\nA new code replaces the previous group. Keep the code within your group.")
        .setPositiveButton(
            "Start & copy code",
            (dialog, which) -> {
              prefs.edit().putString("key", key).apply();
              getSystemService(android.content.ClipboardManager.class)
                  .setPrimaryClip(ClipData.newPlainText("RideWatch join code", key));
              start("host");
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void joinGroup() {
    LinearLayout fields = new LinearLayout(this);
    fields.setOrientation(LinearLayout.VERTICAL);
    fields.setPadding(dp(24), dp(12), dp(24), 0);
    EditText address = new EditText(this);
    address.setHint("Host IP · e.g. 192.168.43.1");
    address.setSingleLine();
    address.setText(prefs.getString("host", ""));
    fields.addView(address);
    EditText key = new EditText(this);
    key.setHint("32-character join code");
    key.setSingleLine();
    key.setText(prefs.getString("key", ""));
    fields.addView(key);
    new AlertDialog.Builder(this)
        .setTitle("Join a local ride group")
        .setView(fields)
        .setMessage("Keep both phone clocks automatic. A lost or delayed stream shows data lost.")
        .setPositiveButton(
            "Join",
            (dialog, which) -> {
              String value = key.getText().toString().trim();
              try {
                new GroupCodec(value);
              } catch (IllegalArgumentException error) {
                detail.setText(error.getMessage());
                return;
              }
              if (address.getText().toString().trim().isEmpty()) {
                detail.setText("Enter the host’s local IP");
                return;
              }
              prefs
                  .edit()
                  .putString("host", address.getText().toString().trim())
                  .putString("key", value)
                  .apply();
              start("join");
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void preview() {
    if (prefs.getBoolean("enabled", false)) {
      detail.setText("Stop monitoring before starting the simulated preview.");
      return;
    }
    demo = true;
    rideButton.setText("End preview");
    handler.removeCallbacksAndMessages(null);
    battery.setText("SIMULATION  ·  NO RADAR CONNECTED");
    for (int i = 0; i < 14; i++) {
      final int step = i;
      handler.postDelayed(
          () -> {
            if (!demo) return;
            int distance = 112 - step * 8;
            var frame =
                new LegacyDecoder()
                    .decode(
                        new byte[] {
                          (byte) ((step << 4) | 2),
                          (byte) (step < 8 ? 0x81 : 0xc1),
                          (byte) distance,
                          80
                        });
            var target = frame.targets.get(0);
            render(
                step < 8 ? "Vehicles approaching" : "Fast approach",
                "Simulated vehicles · distances behind you",
                "LIVE",
                target.threat(),
                new int[] {target.meters(), Math.min(150, distance + 38)},
                new int[] {target.threat(), 1},
                false);
          },
          i * 1000L);
    }
    handler.postDelayed(
        () -> {
          if (demo)
            render(
                "Radar data lost",
                "Preview ended · a lost stream is not an all-clear.",
                "STALE",
                0,
                new int[0],
                new int[0],
                false);
        },
        15000);
  }

  @Override
  public void onDestroy() {
    if (testAudio != null) testAudio.close();
    handler.removeCallbacksAndMessages(null);
    super.onDestroy();
  }
}
