package dev.nelson.ridewatch.core;

/** Only speed and monotonic time are retained, never coordinates. */
public final class RiderSpeed {
  private long measuredAt = -1;
  private double speed = Double.NaN;

  public void accept(double metersPerSecond, double accuracyMps, long timestamp) {
    if (timestamp < measuredAt) return;
    measuredAt = timestamp;
    speed =
        Double.isFinite(metersPerSecond)
                && metersPerSecond >= 0
                && Double.isFinite(accuracyMps)
                && accuracyMps >= 0
                && accuracyMps <= 1.5
            ? metersPerSecond
            : Double.NaN;
  }

  public double at(long now) {
    return measuredAt >= 0 && now >= measuredAt && now - measuredAt <= 3000 ? speed : Double.NaN;
  }

  /** User preference: unavailable rider speed contributes zero, keeping alerts speaking. */
  public double estimateAt(long now) {
    double value = at(now);
    return Double.isFinite(value) ? value : 0;
  }

  public void clear() {
    measuredAt = -1;
    speed = Double.NaN;
  }
}
