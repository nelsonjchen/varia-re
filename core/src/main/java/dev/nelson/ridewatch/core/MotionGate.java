package dev.nelson.ridewatch.core;

/** Requires sustained, recent, accurate movement; a single GPS spike cannot start a ride. */
public final class MotionGate {
  public static final double START_MPS = 4 * 0.44704;
  private long aboveSince = -1, lastSample = -1;

  public boolean update(double speedMps, double accuracyMps, long sampleMs, long nowMs) {
    if (!Double.isFinite(speedMps)
        || !Double.isFinite(accuracyMps)
        || accuracyMps < 0
        || accuracyMps > 1
        || sampleMs > nowMs
        || nowMs - sampleMs > 10_000
        || speedMps - accuracyMps <= START_MPS) {
      aboveSince = -1;
      return false;
    }
    if (lastSample >= 0 && (sampleMs <= lastSample || sampleMs - lastSample > 4000))
      aboveSince = -1;
    lastSample = sampleMs;
    if (aboveSince < 0) aboveSince = sampleMs;
    return sampleMs - aboveSince >= 5_000;
  }

  public void reset() {
    aboveSince = -1;
    lastSample = -1;
  }
}
