package dev.nelson.ridewatch.core;

import java.util.*;

/** Emit on new targets/escalation, never once per BLE packet. Speech queues stay bounded. */
public final class AlertPolicy {
  private final Map<Integer, Integer> announced = new HashMap<>();
  private RadarTracker.Health previousHealth = RadarTracker.Health.DISCONNECTED;
  private long lastSpoken = -10000;

  public String update(RadarTracker.Snapshot snapshot, long now, boolean speakSpeed) {
    if (snapshot.health() != RadarTracker.Health.LIVE) {
      announced.clear();
      boolean lost = previousHealth == RadarTracker.Health.LIVE;
      previousHealth = snapshot.health();
      return lost ? "Radar data lost" : null;
    }
    previousHealth = snapshot.health();
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
    String text = candidate.threat() == 2 ? "Fast vehicle behind" : "Vehicle behind";
    if (speakSpeed)
      text += ", radar speed " + Math.round(candidate.speedMps() / .44704) + " miles per hour";
    return text;
  }

  public void reset() {
    announced.clear();
    previousHealth = RadarTracker.Health.DISCONNECTED;
    lastSpoken = -10000;
  }
}
