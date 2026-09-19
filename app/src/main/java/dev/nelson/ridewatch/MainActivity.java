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
  private TextView headline, detail, targets, battery;
  private LinearLayout content;
  private ScrollView scroll;
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
    scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(dp(24), dp(20), dp(24), dp(36));
    scroll.addView(content);
    setContentView(scroll);
    scroll.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          content.setPadding(
              dp(24),
              dp(20) + insets.getSystemWindowInsetTop(),
              dp(24),
              dp(36) + insets.getSystemWindowInsetBottom());
          return insets;
        });
    text("RIDEWATCH  /  RVR315", 14, LIME);
    text("Know what’s\ncoming behind.", 32, Color.WHITE).setPadding(0, dp(22), 0, dp(22));
    headline = text("Radar is off", 26, MUTED);
    detail = text("Pair your radar, then start a ride or arm motion mode.", 15, MUTED);
    detail.setPadding(0, dp(10), 0, dp(16));
    targets = text("", 20, Color.WHITE);
    targets.setMinHeight(dp(95));
    battery = text("", 13, MUTED);
    button("Pair radar / enable nearby reconnect", this::pair);
    button("Start radar", () -> start("ride"));
    button("Arm motion · above 4 mph", () -> start("motion"));
    button(
        "Stop monitoring",
        () -> {
          demo = false;
          handler.removeCallbacksAndMessages(null);
          prefs.edit().putBoolean("enabled", false).apply();
          stopService(new Intent(this, RadarService.class));
          headline.setText("Radar is off");
          headline.setTextColor(MUTED);
          detail.setText("Monitoring stopped");
          targets.setText("");
        });
    Switch speech = new Switch(this);
    speech.setText("Spoken vehicle alerts");
    speech.setChecked(prefs.getBoolean("speech", true));
    speech.setPadding(0, dp(20), 0, dp(12));
    content.addView(speech);
    speech.setOnCheckedChangeListener(
        (button, value) -> prefs.edit().putBoolean("speech", value).apply());
    Switch speed = new Switch(this);
    speed.setText("Speak radar speed · experimental");
    speed.setChecked(prefs.getBoolean("speed", false));
    content.addView(speed);
    speed.setOnCheckedChangeListener(
        (button, value) -> prefs.edit().putBoolean("speed", value).apply());
    text(
            "Speed units come from Garmin’s app parser. Closing speed and accuracy still need"
                + " RVR315 validation.",
            13,
            MUTED)
        .setPadding(0, dp(10), 0, dp(24));
    text("Ride together", 23, Color.WHITE);
    text(
        "Share the host’s radar over the same Wi-Fi or hotspot. Distances are relative to the host"
            + " rider.",
        14,
        MUTED);
    button("Host a group", this::hostGroup);
    button("Join a group", this::joinGroup);
    button("Preview a simulated approach", this::preview);
    text(
            "Motion mode uses GPS while armed and may use significant battery. Nearby reconnect"
                + " needs Android 12+ companion association. After force-stop, reopen RideWatch."
                + " This prototype has not been tested with a physical radar.",
            13,
            MUTED)
        .setPadding(0, dp(22), 0, 0);
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

  private void button(String label, Runnable action) {
    Button button = new Button(this);
    button.setText(label);
    button.setAllCaps(false);
    button.setMinHeight(dp(52));
    button.setOnClickListener(view -> action.run());
    content.addView(button, new LinearLayout.LayoutParams(-1, -2));
  }

  private final BroadcastReceiver receiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (demo) return;
          headline.setText(
              (intent.getBooleanExtra("group", false) ? "Group · " : "")
                  + intent.getStringExtra("title"));
          String health = intent.getStringExtra("health");
          int threat = intent.getIntExtra("threat", 0);
          headline.setTextColor(
              !"LIVE".equals(health)
                  ? MUTED
                  : threat == 2
                      ? Color.rgb(255, 121, 98)
                      : threat == 1 ? Color.rgb(255, 207, 107) : LIME);
          detail.setText(intent.getStringExtra("detail"));
          targets.setText(intent.getStringExtra("targets"));
          int level = intent.getIntExtra("battery", -1);
          battery.setText(level < 0 ? "" : "Radar battery · " + level + "% at connection");
        }
      };

  @android.annotation.SuppressLint(
      "UnspecifiedRegisterReceiverFlag") // Pre-33 path is guarded and requires our signature
  // permission.
  @Override
  public void onStart() {
    super.onStart();
    if (Build.VERSION.SDK_INT >= 33)
      registerReceiver(
          receiver, new IntentFilter(RadarService.UPDATE), Context.RECEIVER_NOT_EXPORTED);
    else
      registerReceiver(
          receiver, new IntentFilter(RadarService.UPDATE), getPackageName() + ".INTERNAL", null);
  }

  @Override
  public void onStop() {
    unregisterReceiver(receiver);
    super.onStop();
  }

  private void start(String mode) {
    demo = false;
    handler.removeCallbacksAndMessages(null);
    pendingMode = mode;
    ArrayList<String> needed = new ArrayList<>();
    if (Build.VERSION.SDK_INT >= 31) {
      needed.add(Manifest.permission.BLUETOOTH_SCAN);
      needed.add(Manifest.permission.BLUETOOTH_CONNECT);
    }
    if (Build.VERSION.SDK_INT < 31 || "motion".equals(mode)) {
      needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
      needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);
    }
    if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS);
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
      if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) pair();
    }
  }

  private void pair() {
    if (Build.VERSION.SDK_INT >= 31
        && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {Manifest.permission.BLUETOOTH_CONNECT}, 21);
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
          public void onDeviceFound(IntentSender chooser) {
            showChooser(chooser);
          }

          @Override
          public void onAssociationPending(IntentSender chooser) {
            showChooser(chooser);
          }

          @android.annotation.TargetApi(33)
          @Override
          public void onAssociationCreated(AssociationInfo association) {
            if (association.getDeviceMacAddress() != null)
              saveRadar(association.getDeviceMacAddress().toString());
          }

          @Override
          public void onFailure(CharSequence reason) {
            detail.setText("Pairing: " + reason);
          }
        };
    try {
      if (Build.VERSION.SDK_INT >= 33) manager.associate(request, getMainExecutor(), callback);
      else manager.associate(request, callback, handler);
    } catch (RuntimeException error) {
      detail.setText("Pairing unavailable: " + error.getMessage());
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 10 && result == RESULT_OK && data != null && Build.VERSION.SDK_INT < 33) {
      ScanResult device = data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);
      if (device == null) {
        detail.setText("No radar selected");
        return;
      }
      saveRadar(device.getDevice().getAddress());
    }
  }

  private void showChooser(IntentSender chooser) {
    try {
      startIntentSenderForResult(chooser, 10, null, 0, 0, 0);
    } catch (IntentSender.SendIntentException error) {
      detail.setText("Pairing chooser unavailable");
    }
  }

  private void saveRadar(String address) {
    prefs.edit().putString("radar", address).apply();
    if (Build.VERSION.SDK_INT >= 31) {
      try {
        getSystemService(CompanionDeviceManager.class).startObservingDevicePresence(address);
      } catch (RuntimeException error) {
        detail.setText("Radar saved; presence wake unavailable");
        return;
      }
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
    scroll.smoothScrollTo(0, 0);
    handler.removeCallbacksAndMessages(null);
    headline.setText("SIMULATION · vehicle behind");
    headline.setTextColor(Color.rgb(255, 207, 107));
    detail.setText("Synthetic packets through the same decoder · no radar connected");
    battery.setText("");
    for (int i = 0; i < 7; i++) {
      final int step = i;
      handler.postDelayed(
          () -> {
            if (!demo) return;
            var frame =
                new LegacyDecoder()
                    .decode(
                        new byte[] {
                          (byte) ((step << 4) | 2),
                          (byte) (step < 4 ? 0x81 : 0xc1),
                          (byte) (100 - step * 14),
                          80
                        });
            var target = frame.targets.get(0);
            targets.setText(target.meters() + " m behind\n45 mph radar speed");
            if (step >= 4) {
              headline.setText("SIMULATION · fast approach");
              headline.setTextColor(Color.rgb(255, 121, 98));
            }
          },
          i * 1000L);
    }
    handler.postDelayed(
        () -> {
          if (demo) {
            headline.setText("SIMULATION · data lost");
            headline.setTextColor(MUTED);
            targets.setText("Stream expired. Silence is not an all-clear.");
          }
        },
        8500);
  }

  @Override
  public void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    super.onDestroy();
  }
}
