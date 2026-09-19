package dev.nelson.ridewatch.core;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class GroupCodecTest {
  @Test
  public void roundTripAndTamperRejection() throws Exception {
    var codec = new GroupCodec(GroupCodec.newKey());
    var message =
        new GroupCodec.Message(
            42,
            3,
            10000,
            false,
            new RadarTracker.Snapshot(
                RadarTracker.Health.LIVE, List.of(new RadarFrame.Target(1, 2, 55, 12.5)), -1));
    byte[] bytes = codec.encode(message);
    assertEquals(message, codec.decode(bytes, 10001));
    bytes[bytes.length - 1] ^= 1;
    try {
      codec.decode(bytes, 10001);
      fail();
    } catch (Exception expected) {
    }
  }

  @Test
  public void wrongKeyExpiredAndClockSkewRejected() throws Exception {
    var codec = new GroupCodec(GroupCodec.newKey());
    byte[] bytes = codec.encode(new GroupCodec.Message(3, 1, 10000, true, null));
    assertTrue(codec.decode(bytes, 10000).join());
    for (long now : new long[] {14000, 7000})
      try {
        codec.decode(bytes, now);
        fail();
      } catch (Exception expected) {
      }
    try {
      new GroupCodec(GroupCodec.newKey()).decode(bytes, 10000);
      fail();
    } catch (Exception expected) {
    }
  }
}
