package com.mindflakes.ridewatch.core;

import static org.junit.Assert.*;

import com.mindflakes.ridewatch.core.TonePolicy.Tone;
import java.util.List;
import org.junit.Test;

public class TonePolicyTest {
  private RadarTracker.Snapshot live(int threat) {
    return new RadarTracker.Snapshot(
        RadarTracker.Health.LIVE,
        threat < 0 ? List.of() : List.of(new RadarFrame.Target(1, threat, 50, 10)),
        -1);
  }

  @Test
  public void transitionsDoNotRepeatAndEscalationWins() {
    var policy = new TonePolicy();
    assertEquals(Tone.CONNECTED, policy.update(live(-1)));
    assertNull(policy.update(live(-1)));
    assertEquals(Tone.APPROACH, policy.update(live(1)));
    assertNull(policy.update(live(1)));
    assertEquals(Tone.URGENT, policy.update(live(2)));
    assertNull(policy.update(live(2)));
    assertEquals(Tone.EMPTY, policy.update(live(-1)));
    assertNull(policy.update(live(-1)));
  }

  @Test
  public void everyNonLiveStateMeansLossNeverClear() {
    for (var health : RadarTracker.Health.values()) {
      if (health == RadarTracker.Health.LIVE) continue;
      var policy = new TonePolicy();
      assertEquals(Tone.URGENT, policy.update(live(2)));
      var failure = new RadarTracker.Snapshot(health, List.of(), -1);
      assertEquals(Tone.LOST, policy.update(failure));
      assertNull(policy.update(failure));
      assertEquals(Tone.CONNECTED, policy.update(live(-1)));
    }
  }

  @Test
  public void neutralDetectionIsNotClearAndResetIsSilent() {
    var policy = new TonePolicy();
    assertEquals(Tone.APPROACH, policy.update(live(0)));
    assertNull(policy.update(live(0)));
    policy.reset();
    assertNull(
        policy.update(new RadarTracker.Snapshot(RadarTracker.Health.DISCONNECTED, List.of(), -1)));
  }
}
