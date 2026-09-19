package dev.nelson.ridewatch.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class BluetoothAddressTest {
  @Test
  public void companionAddressAndExistingSavedAddressAreCanonicalized() {
    assertEquals("AA:BB:CC:DD:EE:FF", BluetoothAddress.canonicalize("aa:bb:cc:dd:ee:ff"));
    assertEquals("AA:BB:CC:DD:EE:FF", BluetoothAddress.canonicalize(" AA:bB:CC:dd:EE:fF "));
    assertNull(BluetoothAddress.canonicalize(null));
    assertNull(BluetoothAddress.canonicalize(" "));
  }

  @Test
  public void malformedAddressFailsBeforeBluetoothApi() {
    for (String value : new String[] {"AA:BB", "GG:00:00:00:00:00", "AA-BB-CC-DD-EE-FF"}) {
      assertThrows(IllegalArgumentException.class, () -> BluetoothAddress.canonicalize(value));
    }
  }
}
