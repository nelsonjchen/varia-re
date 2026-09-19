package dev.nelson.ridewatch.core;

import java.util.*;

/** Event speech, or paced numeric estimates while targets remain live. */
public final class AlertPolicy {
  private final Map<Integer, Integer> announced = new HashMap<>();
  private RadarTracker.Health previousHealth = RadarTracker.Health.DISCONNECTED;
  private long lastSpoken = -10000;
  private long lastNumber = -10000;

  public String update(RadarTracker.Snapshot snapshot, long now, boolean speakSpeed) {
    return update(snapshot, now, speakSpeed, Double.NaN);
  }

  /** NaN rider speed suppresses estimates; it never silently means stationary. */
  public String update(
      RadarTracker.Snapshot snapshot, long now, boolean speakSpeed, double riderMps) {
    if (snapshot.health() != RadarTracker.Health.LIVE) {
      announced.clear();
      lastNumber = -10000;
      boolean lost = previousHealth == RadarTracker.Health.LIVE;
      previousHealth = snapshot.health();
      return lost ? "Radar data lost" : null;
    }
    previousHealth = snapshot.health();
    if (speakSpeed) {
      if (snapshot.targets().isEmpty()) {
        lastNumber = -10000;
        return null;
      }
      if (!Double.isFinite(riderMps) || riderMps < 0 || now - lastNumber < 2000) return null;
      var selected =
          snapshot.targets().stream()
              .filter(t -> Double.isFinite(t.speedMps()) && t.speedMps() >= 0)
              .min(
                  Comparator.comparingInt(RadarFrame.Target::threat)
                      .reversed()
                      .thenComparingInt(RadarFrame.Target::meters)
                      .thenComparingInt(RadarFrame.Target::id))
              .orElse(null);
      if (selected == null) return null;
      lastNumber = now;
      return Long.toString(Math.round((selected.speedMps() + riderMps) / .44704));
    }
    lastNumber = -10000;
    var present = new HashSet<Integer>();
    for (var target : snapshot.targets()) present.add(target.id());
    announced.keySet().retainAll(present);
    RadarFrame.Target candidate = null;
    for (var target : snapshot.targets()) {
      if (target.threat() > 0
          && target.threat() > announced.getOrDefault(target.id(), -1)
          && (candidate == null || target.threat() > candidate.threat())) candidate = target;
    }
    if (candidate == null || now - lastSpoken < (candidate.threat() == 2 ? 1500 : 4000))
      return null;
    for (var target : snapshot.targets()) announced.put(target.id(), target.threat());
    lastSpoken = now;
    return candidate.threat() == 2 ? "Fast vehicle behind" : "Vehicle behind";
  }

  public void reset() {
    announced.clear();
    previousHealth = RadarTracker.Health.DISCONNECTED;
    lastSpoken = -10000;
    lastNumber = -10000;
  }
}
