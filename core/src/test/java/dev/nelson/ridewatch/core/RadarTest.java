package dev.nelson.ridewatch.core;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class RadarTest {
  private final LegacyDecoder decoder = new LegacyDecoder();

  @Test
  public void packedFieldsAndQuarterMeterSpeed() {
    var frame = decoder.decode(new byte[] {0x12, (byte) 0xc5, 100, 80, (byte) 0x86, 50, 40});
    assertEquals(1, frame.sequence);
    assertEquals(new RadarFrame.Target(5, 2, 100, 20), frame.targets.get(0));
    assertEquals(new RadarFrame.Target(6, 1, 50, 10), frame.targets.get(1));
  }

  @Test
  public void evenFragmentHeaderFollowsAppDispatch() {
    assertEquals(1, decoder.decode(new byte[] {0x24, (byte) 0x81, 20, 8}).targets.size());
  }

  @Test
  public void invalidRangesFilteredAndIdSixBits() {
    var frame =
        decoder.decode(
            new byte[] {
              2, (byte) 0xfd, 0, 0, (byte) 0xff, (byte) 255, 1, (byte) 0xbf, (byte) 154, (byte) 255
            });
    assertEquals(List.of(new RadarFrame.Target(63, 1, 154, 63.75)), frame.targets);
  }

  @Test
  public void malformedAndDiagnosticFrames() {
    for (byte[] packet : new byte[][] {{}, {2, 1}, {1}, {1, 2, 3}}) {
      try {
        decoder.decode(packet);
        fail();
      } catch (IllegalArgumentException expected) {
      }
    }
    assertEquals(RadarFrame.Kind.UNSUPPORTED, decoder.decode(new byte[] {6, 0, 5, 0, 0, 1}).kind);
    assertEquals(7, decoder.decode(new byte[] {1, 7}).error);
  }

  @Test
  public void heartbeatDoesNotInstantlyDeleteTargetsAndSilenceIsStale() {
    var tracker = new RadarTracker();
    tracker.connected();
    assertEquals(RadarTracker.Health.WAITING, tracker.snapshot(0).health());
    tracker.accept(decoder.decode(new byte[] {2, (byte) 0x81, 20, 10}), 0);
    tracker.accept(decoder.decode(new byte[] {0x12}), 1000);
    assertEquals(1, tracker.snapshot(1500).targets().size());
    assertEquals(0, tracker.snapshot(2000).targets().size());
    assertEquals(RadarTracker.Health.LIVE, tracker.snapshot(2000).health());
    assertEquals(RadarTracker.Health.STALE, tracker.snapshot(3000).health());
    tracker.reset();
    assertEquals(RadarTracker.Health.DISCONNECTED, tracker.snapshot(3000).health());
  }

  @Test
  public void diagnosticDoesNotHideStalenessAndErrorClearsTargets() {
    var tracker = new RadarTracker();
    tracker.connected();
    tracker.accept(decoder.decode(new byte[] {2}), 0);
    tracker.accept(decoder.decode(new byte[] {6, 0, 5, 0, 0, 1}), 1900);
    assertEquals(RadarTracker.Health.STALE, tracker.snapshot(2100).health());
    tracker.accept(decoder.decode(new byte[] {1, 4}), 2200);
    assertEquals(RadarTracker.Health.ERROR, tracker.snapshot(2200).health());
  }

  @Test
  public void speechDeduplicatesEscalatesAndAnnouncesLossOnce() {
    var policy = new AlertPolicy();
    var normal =
        new RadarTracker.Snapshot(
            RadarTracker.Health.LIVE, List.of(new RadarFrame.Target(1, 1, 80, 10)), -1);
    assertEquals("Vehicle behind", policy.update(normal, 0, false));
    assertNull(policy.update(normal, 5000, false));
    var high =
        new RadarTracker.Snapshot(
            RadarTracker.Health.LIVE, List.of(new RadarFrame.Target(1, 2, 30, 20)), -1);
    assertEquals("56", policy.update(high, 6000, true, 5));
    var stale = new RadarTracker.Snapshot(RadarTracker.Health.STALE, List.of(), -1);
    assertEquals("Radar data lost", policy.update(stale, 8000, false));
    assertNull(policy.update(stale, 9000, false));
  }
}
