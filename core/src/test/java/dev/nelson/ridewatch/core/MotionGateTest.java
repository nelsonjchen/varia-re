package dev.nelson.ridewatch.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class MotionGateTest {
  @Test
  public void sustainedMovementOnly() {
    MotionGate gate = new MotionGate();
    assertFalse(gate.update(3, .2, 1000, 1000));
    assertFalse(gate.update(3, .2, 4000, 4000));
    assertTrue(gate.update(3, .2, 6000, 6000));
  }

  @Test
  public void gapDoesNotCountAsSustainedMotion() {
    MotionGate gate = new MotionGate();
    assertFalse(gate.update(3, .2, 0, 0));
    assertFalse(gate.update(3, .2, 30000, 30000));
    assertFalse(gate.update(3, .2, 32000, 32000));
    assertTrue(gate.update(3, .2, 35000, 35000));
  }

  @Test
  public void rejectsStaleUncertainAndSpike() {
    MotionGate gate = new MotionGate();
    assertFalse(gate.update(10, .1, 0, 11000));
    assertFalse(gate.update(10, 2, 12000, 12000));
    assertFalse(gate.update(10, .1, 13000, 13000));
    assertFalse(gate.update(0, .1, 16000, 16000));
    assertFalse(gate.update(10, .1, 19000, 19000));
    assertFalse(gate.update(Double.NaN, .1, 24000, 24000));
  }
}
