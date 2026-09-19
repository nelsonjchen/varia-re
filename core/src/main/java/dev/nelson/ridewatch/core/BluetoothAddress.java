package dev.nelson.ridewatch.core;

import java.util.Locale;

/** CDM MacAddress strings are lowercase; BluetoothAdapter requires uppercase hex. */
public final class BluetoothAddress {
  private BluetoothAddress() {}

  public static String canonicalize(String value) {
    if (value == null || value.isBlank()) return null;
    String address = value.trim().toUpperCase(Locale.ROOT);
    if (!address.matches("[0-9A-F]{2}(:[0-9A-F]{2}){5}"))
      throw new IllegalArgumentException("Invalid saved radar address; pair the radar again");
    return address;
  }
}
