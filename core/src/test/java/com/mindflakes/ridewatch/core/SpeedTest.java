package com.mindflakes.ridewatch.core;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class SpeedTest {
  private RadarTracker.Snapshot live(RadarFrame.Target... targets) {
    return new RadarTracker.Snapshot(RadarTracker.Health.LIVE, List.of(targets), -1);
  }

  @Test
  public void numbersRepeatWithUpdatedSpeedsAndStopWhenEmptyOrStale() {
    var policy = new AlertPolicy();
    var car = live(new RadarFrame.Target(1, 1, 80, 10));
    assertEquals("34", policy.update(car, 0, true, 5));
    assertNull(policy.update(car, 500, true, 5));
    assertEquals("45", policy.update(car, 1000, true, 10));
    assertEquals("45", policy.update(car, 2000, true, 10));
    assertNull(policy.update(live(), 2100, true, 10));
    assertEquals("34", policy.update(car, 2200, true, 5));
    var stale = new RadarTracker.Snapshot(RadarTracker.Health.STALE, List.of(), -1);
    assertEquals("Radar data lost", policy.update(stale, 2300, true, 5));
    assertNull(policy.update(stale, 5000, true, 5));
  }

  @Test
  public void unavailableRiderSpeedNeverBecomesZeroAndNeutralTargetsRepeat() {
    var policy = new AlertPolicy();
    var car = live(new RadarFrame.Target(1, 0, 60, 10));
    assertNull(policy.update(car, 0, true, Double.NaN));
    assertNull(policy.update(car, 500, true, -1));
    assertEquals("22", policy.update(car, 1000, true, 0));
    assertNull(policy.update(car, 3000, true, Double.NaN));
    assertEquals("22", policy.update(car, 3500, true, 0));
  }

  @Test
  public void prioritizesThreatThenDistanceRegardlessOfPacketOrder() {
    var farFast = new RadarFrame.Target(1, 2, 100, 20);
    var nearNormal = new RadarFrame.Target(2, 1, 20, 10);
    var nearFast = new RadarFrame.Target(3, 2, 50, 25);
    var policy = new AlertPolicy();
    assertEquals("3 cars. 67", policy.update(live(nearNormal, farFast, nearFast), 0, true, 5));
    assertEquals("67", policy.update(live(nearFast, farFast, nearNormal), 2000, true, 5));
    assertEquals("1 car. 34", policy.update(live(nearNormal), 4000, true, 5));
  }

  @Test
  public void riderSpeedRequiresFreshFiniteAccurateMeasurement() {
    var speed = new RiderSpeed();
    assertTrue(Double.isNaN(speed.at(1000)));
    assertEquals(0, speed.estimateAt(1000), 0);
    speed.accept(5, .5, 1000);
    assertEquals(5, speed.at(4000), 0);
    assertEquals(5, speed.estimateAt(4000), 0);
    assertTrue(Double.isNaN(speed.at(4001)));
    assertEquals(0, speed.estimateAt(4001), 0);
    assertTrue(Double.isNaN(speed.at(999)));
    speed.accept(20, .5, 900); // Out-of-order result cannot replace newer reading.
    assertEquals(5, speed.at(1500), 0);
    speed.accept(7, 2, 2000);
    assertTrue(Double.isNaN(speed.at(2000)));
    speed.accept(0, .1, 3000);
    assertEquals(0, speed.at(3000), 0);
    speed.accept(Double.NaN, .1, 4000);
    assertTrue(Double.isNaN(speed.at(4000)));
    speed.accept(5, Double.NaN, 5000);
    assertTrue(Double.isNaN(speed.at(5000)));
    speed.accept(-1, .1, 6000);
    assertTrue(Double.isNaN(speed.at(6000)));
    speed.accept(5, .1, 7000);
    speed.clear();
    assertTrue(Double.isNaN(speed.at(7000)));
  }
}
