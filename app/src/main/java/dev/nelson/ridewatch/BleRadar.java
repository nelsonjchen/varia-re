package dev.nelson.ridewatch;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.*;
import dev.nelson.ridewatch.core.BluetoothAddress;
import java.util.*;

/** One GATT owner; all state transitions serialized onto main looper. */
@SuppressLint("MissingPermission")
final class BleRadar {
  static final UUID SERVICE = UUID.fromString("6a4e3200-667b-11e3-949a-0800200c9a66");
  static final UUID MEASUREMENT = UUID.fromString("6a4e3203-667b-11e3-949a-0800200c9a66");
  static final UUID CCCD = uuid16("2902");

  static UUID uuid16(String part) {
    return UUID.fromString("0000" + part + "-0000-1000-8000-00805f9b34fb");
  }

  interface Listener {
    void status(String text);

    void connected();

    void disconnected();

    boolean packet(byte[] bytes);

    void battery(int percent);
  }

  private final Context context;
  private final Listener listener;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private BluetoothGatt gatt;
  private BluetoothLeScanner scanner;
  private boolean running, subscribed;
  private String address;
  private int retries;
  private long lastPacket;
  private final Runnable watchdog =
      new Runnable() {
        public void run() {
          if (!running) return;
          if (subscribed && SystemClock.elapsedRealtime() - lastPacket > 15000)
            retry("No radar data; reconnecting");
          handler.postDelayed(this, 5000);
        }
      };

  BleRadar(Context context, Listener listener) {
    this.context = context;
    this.listener = listener;
  }

  boolean permissions() {
    return (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED
        && context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
            == PackageManager.PERMISSION_GRANTED);
  }

  void start(String savedAddress) {
    stop();
    running = true;
    try {
      address = BluetoothAddress.canonicalize(savedAddress);
    } catch (IllegalArgumentException error) {
      running = false;
      listener.status(error.getMessage());
      return;
    }
    if (address != null && !address.equals(savedAddress))
      context.getSharedPreferences("ride", 0).edit().putString("radar", address).apply();
    retries = 0;
    attempt();
    handler.postDelayed(watchdog, 5000);
  }

  void stop() {
    running = false;
    handler.removeCallbacksAndMessages(null);
    close();
    listener.disconnected();
  }

  private void close() {
    subscribed = false;
    if (scanner != null) {
      try {
        scanner.stopScan(scan);
      } catch (RuntimeException ignored) {
      }
      scanner = null;
    }
    if (gatt != null) {
      BluetoothGatt old = gatt;
      gatt = null;
      try {
        old.disconnect();
        old.close();
      } catch (RuntimeException ignored) {
      }
    }
  }

  private void attempt() {
    if (!running) return;
    try {
      if (!permissions()) {
        listener.status("Bluetooth permission needed");
        return;
      }
      BluetoothAdapter adapter = context.getSystemService(BluetoothManager.class).getAdapter();
      if (adapter == null || !adapter.isEnabled()) {
        retry("Turn on Bluetooth; retrying");
        return;
      }
      if (address != null && !address.isBlank()) {
        listener.status("Connecting to saved radar…");
        connect(adapter.getRemoteDevice(address));
      } else {
        listener.status("Searching for radar…");
        scanner = adapter.getBluetoothLeScanner();
        scanner.startScan(
            List.of(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE)).build()),
            new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            scan);
        handler.postDelayed(timeout, 15000);
      }
    } catch (RuntimeException error) {
      android.util.Log.e("RideWatchBle", "Bluetooth connection attempt failed", error);
      retry("Bluetooth unavailable: " + error.getClass().getSimpleName());
    }
  }

  private final Runnable timeout = () -> retry("Connection timed out; retrying");

  private void connect(BluetoothDevice device) {
    if (scanner != null) {
      scanner.stopScan(scan);
      scanner = null;
    }
    handler.removeCallbacks(timeout);
    gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE);
    handler.postDelayed(timeout, 20000);
  }

  private void retry(String message) {
    if (!running) return;
    close();
    handler.removeCallbacks(timeout);
    handler.removeCallbacks(retryTask);
    listener.disconnected();
    listener.status(message);
    handler.postDelayed(retryTask, Math.min(30000, 1000L << Math.min(retries++, 5)));
  }

  private final Runnable retryTask = this::attempt;
  private final ScanCallback scan =
      new ScanCallback() {
        @Override
        public void onScanResult(int type, ScanResult result) {
          handler.post(
              () -> {
                if (running && scanner != null) {
                  address = result.getDevice().getAddress();
                  context
                      .getSharedPreferences("ride", 0)
                      .edit()
                      .putString("radar", address)
                      .apply();
                  connect(result.getDevice());
                }
              });
        }

        @Override
        public void onScanFailed(int code) {
          handler.post(() -> retry("Scan failed (" + code + "); retrying"));
        }
      };
  private final BluetoothGattCallback callback =
      new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt client, int code, int state) {
          handler.post(
              () -> {
                if (client != gatt || !running) return;
                if (code == BluetoothGatt.GATT_SUCCESS
                    && state == BluetoothProfile.STATE_CONNECTED) {
                  listener.status("Reading radar services…");
                  if (!client.discoverServices()) retry("Service discovery failed");
                } else if (state == BluetoothProfile.STATE_DISCONNECTED
                    || code != BluetoothGatt.GATT_SUCCESS) retry("Radar disconnected; retrying");
              });
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt client, int code) {
          handler.post(
              () -> {
                if (client != gatt || !running) return;
                BluetoothGattService service = client.getService(SERVICE);
                BluetoothGattCharacteristic measurement =
                    service == null ? null : service.getCharacteristic(MEASUREMENT);
                if (code != 0 || measurement == null) {
                  retry("Legacy radar service not found");
                  return;
                }
                BluetoothGattDescriptor descriptor = measurement.getDescriptor(CCCD);
                if (descriptor == null
                    || !client.setCharacteristicNotification(measurement, true)) {
                  retry("Radar subscription unavailable");
                  return;
                }
                if (client.writeDescriptor(
                        descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    != BluetoothStatusCodes.SUCCESS) retry("Radar subscription failed");
              });
        }

        @Override
        public void onDescriptorWrite(
            BluetoothGatt client, BluetoothGattDescriptor descriptor, int code) {
          handler.post(
              () -> {
                if (client != gatt || !running) return;
                if (code != 0) {
                  retry("Subscription rejected (" + code + ")");
                  return;
                }
                handler.removeCallbacks(timeout);
                subscribed = true;
                retries = 0;
                lastPacket = SystemClock.elapsedRealtime();
                listener.connected();
                listener.status("Connected · waiting for radar data");
                BluetoothGattService battery = client.getService(uuid16("180f"));
                if (battery != null && battery.getCharacteristic(uuid16("2a19")) != null)
                  client.readCharacteristic(battery.getCharacteristic(uuid16("2a19")));
              });
        }

        @Override
        public void onCharacteristicRead(
            BluetoothGatt client,
            BluetoothGattCharacteristic characteristic,
            byte[] bytes,
            int code) {
          handler.post(
              () -> {
                if (client == gatt
                    && code == 0
                    && characteristic.getUuid().equals(uuid16("2a19"))
                    && bytes.length > 0
                    && (bytes[0] & 255) <= 100) listener.battery(bytes[0] & 255);
              });
        }

        @Override
        public void onCharacteristicChanged(
            BluetoothGatt client, BluetoothGattCharacteristic characteristic, byte[] bytes) {
          byte[] copy = bytes.clone();
          handler.post(
              () -> {
                if (client != gatt || !subscribed || !MEASUREMENT.equals(characteristic.getUuid()))
                  return;
                if (listener.packet(copy)) lastPacket = SystemClock.elapsedRealtime();
              });
        }
      };
}
